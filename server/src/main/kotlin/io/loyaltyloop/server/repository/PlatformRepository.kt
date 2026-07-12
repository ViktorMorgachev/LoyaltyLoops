package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.PlatformRequestsTable
import io.loyaltyloop.server.database.tables.PlatformSubscriptionsTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePlatformRequest
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.PlatformRequestDto
import io.loyaltyloop.shared.models.PlatformRequestStatus
import io.loyaltyloop.shared.models.PlatformRequestType
import io.loyaltyloop.shared.models.SubscriptionDuration
import io.loyaltyloop.shared.models.SubscriptionType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime

// TODO checked
private const val TRIAL_PERIOD_DAYS = 14L

class PlatformRepository(val systemEventRepository: SystemEventRepository) {

    private val requesterStaff = SystemStaffTable.alias("req_staff")
    private val requesterUser = UsersTable.alias("req_user")

    private val approverStaff = SystemStaffTable.alias("app_staff")
    private val approverUser = UsersTable.alias("app_user")

    suspend fun createRequest(userId: String, request: CreatePlatformRequest): String = dbQuery {
        val userUuid = userId.toUUID()
        val pointUuid = request.targetPointId.toUUID()

        val staffId = SystemStaffTable
            .slice(SystemStaffTable.id)
            .select {
                (SystemStaffTable.user eq userUuid) and
                        (SystemStaffTable.isActive eq true)
            }
            .singleOrNull()
            ?.get(SystemStaffTable.id)
            ?: throw LoyaltyException(AppErrorCode.FORBIDDEN, "Only Platform Staff can create requests")


        val pointRow = TradingPointsTable.slice(TradingPointsTable.partner)
            .select { TradingPointsTable.id eq pointUuid }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Invalid Point ID")

        val partnerId = pointRow[TradingPointsTable.partner].value

        if (request.isTrial) {

            val hasUsedTrial = PlatformSubscriptionsTable
                .select {
                    (PlatformSubscriptionsTable.tradingPoint eq pointUuid) and
                            (PlatformSubscriptionsTable.isTrial eq true)
                }
                .count() > 0

            val hasPendingTrialRequest = PlatformRequestsTable
                .select {
                    (PlatformRequestsTable.targetPoint eq pointUuid) and
                            (PlatformRequestsTable.isTrial eq true) and
                            (PlatformRequestsTable.status eq PlatformRequestStatus.PENDING)
                }
                .count() > 0

            if (hasUsedTrial || hasPendingTrialRequest) {
                throw LoyaltyException(
                    AppErrorCode.TRIAL_ALREADY_USED,
                    "Point has already used a trial or request is pending."
                )
            }
        }

        // 3. Создаем заявку
        val newRequestId = PlatformRequestsTable.insertAndGetId {
            it[type] = request.type
            it[status] = PlatformRequestStatus.PENDING
            it[requester] = staffId
            it[targetPoint] = pointUuid
            it[targetPartner] = partnerId
            it[amount] = request.amount?.toBigDecimal()
            it[duration] = request.duration
            it[isTrial] = request.isTrial
            it[blockReason] = request.blockReason

        }

        systemEventRepository.logEvent(
            type = SystemEventType.SUBSCRIPTION_CREATED,
            userId = userId,
            partnerId = partnerId.toString(),
            payload = "Request Created: ${request.type} for Point ${request.targetPointId}"
        )

        newRequestId.value.toString()
    }

    suspend fun getRequests(
        status: PlatformRequestStatus? = null,
        requesterId: String? = null, // Это userId
        targetPartnerId: String? = null
    ): List<PlatformRequestDto> = dbQuery {

        val query = PlatformRequestsTable
            // 1. Реквестер
            .join(requesterStaff, JoinType.LEFT, PlatformRequestsTable.requester, requesterStaff[SystemStaffTable.id])
            .join(requesterUser, JoinType.LEFT, requesterStaff[SystemStaffTable.user], requesterUser[UsersTable.id])
            // 2. Аппрувер
            .join(approverStaff, JoinType.LEFT, PlatformRequestsTable.approver, approverStaff[SystemStaffTable.id])
            .join(approverUser, JoinType.LEFT, approverStaff[SystemStaffTable.user], approverUser[UsersTable.id])
            // 3. Контекст
            .join(TradingPointsTable, JoinType.LEFT, PlatformRequestsTable.targetPoint, TradingPointsTable.id)
            .join(PartnersTable, JoinType.LEFT, PlatformRequestsTable.targetPartner, PartnersTable.id)
            .selectAll()

        // Фильтры
        if (status != null) {
            query.andWhere { PlatformRequestsTable.status eq status }
        }
        if (requesterId != null) {
            // Фильтруем по UserID реквестера (через алиас)
            query.andWhere { requesterStaff[SystemStaffTable.user] eq requesterId.toUUID() }
        }
        if (targetPartnerId != null) {
            query.andWhere { PlatformRequestsTable.targetPartner eq targetPartnerId.toUUID() }
        }

        // 3. Сортировка и Маппинг
        query.orderBy(PlatformRequestsTable.createdAt to SortOrder.DESC)
            .map { row ->
                // Имя Реквестера
                val reqFirst = row.getOrNull(requesterUser[UsersTable.firstName]) ?: ""
                val reqLast = row.getOrNull(requesterUser[UsersTable.lastName]) ?: ""
                val reqName = "$reqFirst $reqLast".trim().ifBlank { "Unknown Staff" }
                val reqPhone = row.getOrNull(requesterUser[UsersTable.phoneNumber]) ?: ""

                // Имя Аппрувера
                val appFirst = row.getOrNull(approverUser[UsersTable.firstName])
                val appName = if (appFirst != null) {
                    "$appFirst ${row.getOrNull(approverUser[UsersTable.lastName]) ?: ""}".trim()
                } else null

                val reqUserId = row.getOrNull(requesterUser[UsersTable.id])?.value?.toString() ?: ""

                PlatformRequestDto(
                    id = row[PlatformRequestsTable.id].value.toString(),
                    type = row[PlatformRequestsTable.type],
                    status = row[PlatformRequestsTable.status],
                    requesterId = reqUserId,
                    requesterName = reqName,
                    requesterPhone = reqPhone,
                    approverId = row.getOrNull(approverUser[UsersTable.id])?.value?.toString(),
                    approverName = appName,
                    createdAt = row[PlatformRequestsTable.createdAt].toUtcMillis(),
                    updatedAt = row[PlatformRequestsTable.updatedAt].toUtcMillis(),
                    targetPartnerId = row[PlatformRequestsTable.targetPartner]?.value?.toString(),
                    targetPartnerName = row.getOrNull(PartnersTable.businessName),
                    targetPointId = row[PlatformRequestsTable.targetPoint]?.value?.toString(),
                    targetPointName = row.getOrNull(TradingPointsTable.name),
                    amount = row[PlatformRequestsTable.amount]?.toDouble(),
                    duration = row[PlatformRequestsTable.duration],
                    isTrial = row[PlatformRequestsTable.isTrial],
                    blockReason = row[PlatformRequestsTable.blockReason],
                    rejectReason = row[PlatformRequestsTable.rejectReason]
                )
            }
    }

    suspend fun approveRequest(requestId: String, approverUserId: String): Boolean {
        val requestUuid = requestId.toUUID()
        val now = nowUtc()
        dbQuery {

            val approverStaffId = SystemStaffTable
                .slice(SystemStaffTable.id)
                .select { (SystemStaffTable.user eq approverUserId.toUUID()) and (SystemStaffTable.isActive eq true) }
                .singleOrNull()
                ?.get(SystemStaffTable.id)
                ?: throw LoyaltyException(AppErrorCode.FORBIDDEN, "User is not active platform staff")

            val requestRow = PlatformRequestsTable
                .selectAll()
                .where { PlatformRequestsTable.id eq requestUuid }
                .singleOrNull()
                ?: throw LoyaltyException(AppErrorCode.NOT_FOUND, "Request not found")

            // C. Валидация статуса
            if (requestRow[PlatformRequestsTable.status] != PlatformRequestStatus.PENDING) {
                throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Request is not pending")
            }

            val type = requestRow[PlatformRequestsTable.type]
            val pointId = requestRow[PlatformRequestsTable.targetPoint] // EntityID<UUID>

            val partnerId = requestRow[PlatformRequestsTable.targetPartner]?.value
                ?: if (pointId != null) {
                    TradingPointsTable.slice(TradingPointsTable.partner)
                        .select { TradingPointsTable.id eq pointId }
                        .single()[TradingPointsTable.partner].value
                } else null

            if (partnerId == null && pointId == null) throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Target Context missing")

            // D. Проверка статуса Партнера (через ID бизнеса!)
            val partnerStatus = PartnersTable
                .slice(PartnersTable.status)
                .select { PartnersTable.id eq partnerId }
                .single()[PartnersTable.status]

            if (type == PlatformRequestType.ACTIVATE_POINT) {
                if (partnerStatus == PartnerStatus.PENDING) {
                    throw LoyaltyException(AppErrorCode.PARTNER_ON_REVIEW, "Partner is under review")
                }
                if (partnerStatus == PartnerStatus.BLOCKED) throw LoyaltyException(AppErrorCode.PARTNER_BLOCKED, "Partner is blocked")
            }

            when (type) {
                PlatformRequestType.ACTIVATE_POINT -> {
                    if (pointId == null) throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Point ID missing for Activation")
                    val maxCurrentEndDate = PlatformSubscriptionsTable
                        .slice(PlatformSubscriptionsTable.endDate)
                        .select {
                            (PlatformSubscriptionsTable.tradingPoint eq pointId) and
                                    (PlatformSubscriptionsTable.isActive eq true)
                        }
                        .orderBy(PlatformSubscriptionsTable.endDate, SortOrder.DESC)
                        .limit(1)
                        .map { it[PlatformSubscriptionsTable.endDate] }
                        .singleOrNull()

                    val effectiveStartDate = if (maxCurrentEndDate != null && maxCurrentEndDate.isAfter(now)) {
                        maxCurrentEndDate
                    } else {
                        now
                    }
                    val isTrial = requestRow[PlatformRequestsTable.isTrial]

                    val endDate = if (isTrial) {
                        effectiveStartDate.plusDays(TRIAL_PERIOD_DAYS)
                    } else {
                        val duration = requestRow[PlatformRequestsTable.duration]
                        calculateEndDate(effectiveStartDate, duration!!)
                    }

                    PlatformRequestsTable.update({ PlatformRequestsTable.id eq requestUuid }) {
                        it[status] = PlatformRequestStatus.APPROVED
                        it[approver] = approverStaffId
                        it[updatedAt] = now
                    }
                    // 4. Создаем подписку
                    PlatformSubscriptionsTable.insert {
                        it[tradingPoint] = pointId
                        it[requester] = requestRow[PlatformRequestsTable.requester]
                        it[this.type] = SubscriptionType.FIXED_TERM
                        it[startDate] = effectiveStartDate
                        it[this.endDate] = endDate
                        it[amount] = requestRow[PlatformRequestsTable.amount] ?: BigDecimal.ZERO
                        it[this.isTrial] = isTrial
                        it[updatedAt] = now
                        it[isActive] = true // Подписка активна
                    }

                    // 5. Активируем точку и Партнера (если нужно)
                    TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
                        it[isActive] = true
                        it[updatedAt] = now
                        it[isTemporarilyPaused] = false
                    }
                    systemEventRepository.logEvent(
                        type = SystemEventType.SUBSCRIPTION_ACTIVATED,
                        userId = approverUserId,
                        partnerId = partnerId.toString(),
                        payload = "APPROVED: ACTIVATE_POINT $pointId. Valid until $endDate"
                    )
                }

                PlatformRequestType.BLOCK_PARTNER -> {
                    PartnersTable.update({ PartnersTable.id eq partnerId }) {
                        it[status] = PartnerStatus.BLOCKED
                        it[updatedAt] = now
                    }
                    // Точки не отключаем, просто статус партнера блокирует доступ к API
                    systemEventRepository.logEvent(
                        type = SystemEventType.PARTNER_BLOCKED,
                        userId = approverUserId,
                        partnerId = partnerId.toString(),
                        payload = "Reason: ${requestRow[PlatformRequestsTable.blockReason]}"
                    )
                }

                PlatformRequestType.UNBLOCK_PARTNER -> {
                    PartnersTable.update({ PartnersTable.id eq partnerId }) {
                        it[status] = PartnerStatus.ACTIVE
                        it[updatedAt] = now
                    }
                    systemEventRepository.logEvent(
                        type = SystemEventType.PARTNER_UNBLOCKED,
                        userId = approverUserId,
                        partnerId = partnerId.toString(),
                        payload = "Partner Unblocked"
                    )
                }
            }
        }
        return true
    }

    @Suppress("MagicNumber") // числа продублированы в именах enum-констант (MONTH_3 -> plusMonths(3))
    private fun calculateEndDate(start: LocalDateTime, duration: SubscriptionDuration): java.time.LocalDateTime {
        return when (duration) {
            SubscriptionDuration.MONTH_1 -> start.plusMonths(1)
            SubscriptionDuration.MONTH_3 -> start.plusMonths(3)
            SubscriptionDuration.MONTH_6 -> start.plusMonths(6)
            SubscriptionDuration.YEAR_1 -> start.plusYears(1)
            SubscriptionDuration.DAY_1 -> start.plusDays(1)
            SubscriptionDuration.DAY_3 ->  start.plusDays(3)
            SubscriptionDuration.WEEK_2 ->  start.plusWeeks(2)
            SubscriptionDuration.YEAR_2 -> start.plusYears(2)
            SubscriptionDuration.YEAR_3 -> start.plusYears(3)
            SubscriptionDuration.YEAR_5 -> start.plusYears(5)
        }
    }

    suspend fun rejectRequest(requestId: String, approverId: String, reason: String): Boolean {
        val requestUuid = requestId.toUUID()
        val now = nowUtc()

        dbQuery {
            val approverStaffId = SystemStaffTable
                .slice(SystemStaffTable.id)
                .select { (SystemStaffTable.user eq approverId.toUUID()) and (SystemStaffTable.isActive eq true) }
                .singleOrNull()
                ?.get(SystemStaffTable.id)
                ?: throw LoyaltyException(
                    AppErrorCode.FORBIDDEN,
                    "User is not active platform staff"
                )

            // 2. Получаем заявку (нам нужны данные для логов)
            val requestRow = PlatformRequestsTable
                .selectAll()
                .where { PlatformRequestsTable.id eq requestUuid }
                .singleOrNull()
                ?: throw LoyaltyException(AppErrorCode.NOT_FOUND, "Request not found")

            val pointId = requestRow[PlatformRequestsTable.targetPoint] // EntityID<UUID>

            val partnerId = requestRow[PlatformRequestsTable.targetPartner]?.value
                ?: if (pointId != null) {
                    TradingPointsTable.slice(TradingPointsTable.partner)
                        .select { TradingPointsTable.id eq pointId }
                        .single()[TradingPointsTable.partner].value
                } else null

            if (partnerId == null && pointId == null) throw LoyaltyException(
                AppErrorCode.INTERNAL_ERROR,
                "Target Context missing"
            )

            PlatformRequestsTable.update({ PlatformRequestsTable.id eq requestUuid }) {
                it[status] = PlatformRequestStatus.REJECTED
                it[approver] = approverStaffId
                it[rejectReason] = reason
                it[updatedAt] = now
            }

            systemEventRepository.logEvent(
                type = SystemEventType.SUBSCRIPTION_REJECTED,
                userId = approverId,
                partnerId = partnerId?.toString(),
                payload = "REJECTED: ${requestRow[PlatformRequestsTable.type]}. Reason: $reason"
            )
        }
        return true
    }

    suspend fun updatePartnerStatus(partnerId: String, status: PartnerStatus) {
        val uuid = partnerId.toUUID()
        val now = nowUtc()
        dbQuery {
            PartnersTable.update({ PartnersTable.id eq uuid }) {
                it[this.status] = status
                it[updatedAt] = now
            }
        }
    }
}

package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.*
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.models.SubscriptionWarningDto
import io.loyaltyloop.server.service.email.EmailService
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class PlatformRepository {

    private val systemEventRepository = SystemEventRepository()

    // Создаем Алиас таблицы юзеров, чтобы джойнить её дважды (для Реквестера и для Аппрувера)
    private val ApproverUserTable = UsersTable.alias("approver_users")

    // --- SUBSCRIPTIONS ---

    suspend fun getExpiringSubscriptions(): List<SubscriptionWarningDto> = dbQuery {
        val now = System.currentTimeMillis()
        val warningThreshold = now + (3 * 24 * 60 * 60 * 1000L) // 3 days

        // Оптимизация: выбираем только нужные поля
        val allSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable)
            .slice(
                TradingPointsTable.id, TradingPointsTable.name, TradingPointsTable.partnerId,
                PartnersTable.businessName, PlatformSubscriptionsTable.endDate
            )
            .select {
                (PlatformSubscriptionsTable.isActive eq true) and
                        (PlatformSubscriptionsTable.endDate.isNotNull())
            }
            .map { row ->
                Triple(
                    row[TradingPointsTable.id],
                    row[PlatformSubscriptionsTable.endDate]!!,
                    row
                )
            }

        // Группируем по PointID, находим максимальную дату окончания
        val maxEndDates = allSubs
            .groupBy { it.first }
            .mapValues { (_, subs) -> subs.maxOf { it.second } }

        // Фильтруем
        allSubs.filter { (pointId, endDate, _) ->
            val maxDate = maxEndDates[pointId] ?: return@filter false

            // Если максимальная дата больше порога, значит точка в безопасности (продлена)
            if (maxDate > warningThreshold) return@filter false
            // Если уже истекла (меньше сейчас) - это не warning, а факт (обрабатывается джобом)
            if (maxDate < now) return@filter false

            // Возвращаем warning только для той подписки, которая является последней
            endDate == maxDate
        }.map { (_, _, row) ->
            SubscriptionWarningDto(
                partnerId = row[TradingPointsTable.partnerId],
                partnerName = row[PartnersTable.businessName],
                pointId = row[TradingPointsTable.id],
                pointName = row[TradingPointsTable.name],
                endDate = row[PlatformSubscriptionsTable.endDate]!!
            )
        }.distinctBy { it.pointId } // На всякий случай убираем дубли по точкам
    }

    // --- REQUESTS ---


    suspend fun createRequest(requesterId: String, request: CreatePlatformRequest): String =
        dbQuery {
            val targetPointId = request.targetPointId
            val now = System.currentTimeMillis()

            // 1. Проверяем валидность точки
            val partnerId = TradingPointsTable.slice(TradingPointsTable.partnerId)
                .select { TradingPointsTable.id eq targetPointId }
                .singleOrNull()?.get(TradingPointsTable.partnerId)
                ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Invalid Point ID")

            // 2. Проверка Триала (Уже использовали ИЛИ уже есть заявка)
            if (request.isTrial) {
                val hasUsedTrial = PlatformSubscriptionsTable
                    .select { (PlatformSubscriptionsTable.pointId eq targetPointId) and (PlatformSubscriptionsTable.isTrial eq true) }
                    .count() > 0

                val hasPendingTrialRequest = PlatformRequestsTable
                    .select {
                        (PlatformRequestsTable.targetPointId eq targetPointId) and
                                (PlatformRequestsTable.isTrial eq true) and
                                (PlatformRequestsTable.status eq PlatformRequestStatus.PENDING.name)
                    }
                    .count() > 0

                if (hasUsedTrial || hasPendingTrialRequest) {
                    throw LoyaltyException(
                        AppErrorCode.TRIAL_ALREADY_USED,
                        "Point has already used a trial or request is pending."
                    )
                }
            }

            val newId = UUID.randomUUID().toString()

            PlatformRequestsTable.insert {
                it[id] = newId
                it[type] = request.type.name
                it[status] = PlatformRequestStatus.PENDING.name
                it[this.requesterId] = requesterId
                it[this.targetPointId] = targetPointId
                it[amount] = request.amount
                it[duration] = request.duration?.name
                it[isTrial] = request.isTrial
                it[blockReason] = request.blockReason
                it[createdAt] = now
                it[updatedAt] = now
            }

            systemEventRepository.logEvent(
                type = SystemEventType.INFO,
                userId = requesterId,
                partnerId = partnerId,
                payload = "Request Created: ${request.type} for Point $targetPointId"
            )
            newId
        }

    suspend fun getRequests(
        status: PlatformRequestStatus? = null,
        requesterId: String? = null,
        targetPartnerId: String? = null
    ): List<PlatformRequestDto> = dbQuery {

        // Сложный JOIN: Requests -> Users(Req) -> Users(Appr) -> Points -> Partners
        val query = PlatformRequestsTable
            .join(UsersTable, JoinType.LEFT, PlatformRequestsTable.requesterId, UsersTable.id)
            .join(
                ApproverUserTable,
                JoinType.LEFT,
                PlatformRequestsTable.approverId,
                ApproverUserTable[UsersTable.id]
            )
            .join(
                TradingPointsTable,
                JoinType.INNER,
                PlatformRequestsTable.targetPointId,
                TradingPointsTable.id
            )
            .join(PartnersTable, JoinType.INNER, TradingPointsTable.partnerId, PartnersTable.id)
            .selectAll()

        // Фильтры
        status?.let { query.andWhere { PlatformRequestsTable.status eq it.name } }
        requesterId?.let { query.andWhere { PlatformRequestsTable.requesterId eq it } }
        targetPartnerId?.let { query.andWhere { PartnersTable.id eq it } }

        query.orderBy(PlatformRequestsTable.createdAt to SortOrder.DESC)
            .map { row ->
                // Собираем имя Аппрувера из алиаса
                val approverFirst = row.getOrNull(ApproverUserTable[UsersTable.firstName])
                val approverLast = row.getOrNull(ApproverUserTable[UsersTable.lastName])
                val approverFullName =
                    if (approverFirst != null) "$approverFirst $approverLast".trim() else null

                // Собираем имя Реквестера из основной таблицы
                val reqName =
                    "${row[UsersTable.firstName] ?: ""} ${row[UsersTable.lastName] ?: ""}".trim()

                PlatformRequestDto(
                    id = row[PlatformRequestsTable.id],
                    type = PlatformRequestType.valueOf(row[PlatformRequestsTable.type]),
                    status = PlatformRequestStatus.valueOf(row[PlatformRequestsTable.status]),
                    requesterId = row[PlatformRequestsTable.requesterId],
                    requesterName = reqName.ifBlank { "Unknown" },
                    requesterPhone = row[UsersTable.phoneNumber],
                    approverId = row[PlatformRequestsTable.approverId],
                    approverName = approverFullName,
                    createdAt = row[PlatformRequestsTable.createdAt],
                    updatedAt = row[PlatformRequestsTable.updatedAt],
                    targetPartnerId = row[PartnersTable.id],
                    targetPartnerName = row[PartnersTable.businessName],
                    targetPointId = row[PlatformRequestsTable.targetPointId],
                    targetPointName = row[TradingPointsTable.name],
                    currency = row[TradingPointsTable.currency],
                    amount = row[PlatformRequestsTable.amount],
                    duration = row[PlatformRequestsTable.duration]?.let {
                        SubscriptionDuration.valueOf(
                            it
                        )
                    },
                    isTrial = row[PlatformRequestsTable.isTrial],
                    blockReason = row[PlatformRequestsTable.blockReason],
                    rejectReason = row[PlatformRequestsTable.rejectReason]
                )
            }
    }

    suspend fun approveRequest(requestId: String, approverId: String, emailService: EmailService) {
        // 1. Transaction: Logic & Data preparation
        val emailToNotify = dbQuery {
            val requestRow = PlatformRequestsTable.selectAll()
                .where { PlatformRequestsTable.id eq requestId }
                .singleOrNull() ?: throw LoyaltyException(
                AppErrorCode.NOT_FOUND,
                "Request not found"
            )

            if (PlatformRequestStatus.valueOf(requestRow[PlatformRequestsTable.status]) != PlatformRequestStatus.PENDING) {
                throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Request is not pending")
            }

            val type = PlatformRequestType.valueOf(requestRow[PlatformRequestsTable.type])
            val pointId = requestRow[PlatformRequestsTable.targetPointId]
                ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Target Point ID missing")

            val partnerRow = TradingPointsTable.innerJoin(PartnersTable)
                .slice(TradingPointsTable.partnerId, PartnersTable.status)
                .select { TradingPointsTable.id eq pointId }
                .single()

            val partnerId = partnerRow[TradingPointsTable.partnerId]
            val partnerStatus = partnerRow[PartnersTable.status]

            // Валидация статуса партнера
            if (type == PlatformRequestType.ACTIVATE_POINT) {
                if (partnerStatus == PartnerStatus.PENDING) throw LoyaltyException(
                    AppErrorCode.PARTNER_ON_REVIEW,
                    "Partner is under review"
                )
                if (partnerStatus == PartnerStatus.BLOCKED) throw LoyaltyException(
                    AppErrorCode.PARTNER_BLOCKED,
                    "Partner is blocked"
                )
            }

            val now = System.currentTimeMillis()

            // Update Request
            PlatformRequestsTable.update({ PlatformRequestsTable.id eq requestId }) {
                it[status] = PlatformRequestStatus.APPROVED.name
                it[this.approverId] = approverId
                it[updatedAt] = now
            }

            when (type) {
                PlatformRequestType.ACTIVATE_POINT -> {
                    // --- SMART EXTENSION LOGIC ---
                    // Находим конец текущей активной подписки
                    val maxCurrentEndDate = PlatformSubscriptionsTable
                        .slice(PlatformSubscriptionsTable.endDate.max())
                        .select { (PlatformSubscriptionsTable.pointId eq pointId) and (PlatformSubscriptionsTable.isActive eq true) }
                        .singleOrNull()
                        ?.get(PlatformSubscriptionsTable.endDate.max())

                    // Если подписка активна и заканчивается в будущем, новая стартует после неё
                    val effectiveStartDate =
                        if (maxCurrentEndDate != null && maxCurrentEndDate > now) maxCurrentEndDate else now

                    // Деактивируем старые (архивируем)
                    PlatformSubscriptionsTable.update({ (PlatformSubscriptionsTable.pointId eq pointId) and (PlatformSubscriptionsTable.isActive eq true) }) {
                        it[isActive] = false
                    }

                    val durationName = requestRow[PlatformRequestsTable.duration] ?: "MONTH_1"
                    val isTrial = requestRow[PlatformRequestsTable.isTrial]

                    val endDate = if (isTrial) {
                        Instant.ofEpochMilli(effectiveStartDate).plus(14, ChronoUnit.DAYS)
                            .toEpochMilli()
                    } else {
                        calculateEndDate(
                            effectiveStartDate,
                            SubscriptionDuration.valueOf(durationName)
                        )
                    }

                    PlatformSubscriptionsTable.insert {
                        it[id] = UUID.randomUUID().toString()
                        it[this.pointId] = pointId
                        it[this.requesterId] = requestRow[PlatformRequestsTable.requesterId]
                        it[this.type] = "FIXED_TERM"
                        it[startDate] = effectiveStartDate
                        it[this.endDate] = endDate
                        it[amount] = requestRow[PlatformRequestsTable.amount] ?: 0.0
                        it[this.isTrial] = isTrial
                        it[isActive] = true
                        it[createdAt] = now
                    }

                    TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
                        it[isActive] = true
                    }
                    PartnersTable.update({ PartnersTable.id eq partnerId }) {
                        it[status] = PartnerStatus.ACTIVE
                    }

                    systemEventRepository.logEvent(
                        type = SystemEventType.INFO,
                        userId = approverId,
                        partnerId = partnerId,
                        payload = "APPROVED: ACTIVATE_POINT $pointId"
                    )

                    // Return email
                    UsersTable.slice(UsersTable.email)
                        .select { UsersTable.id eq requestRow[PlatformRequestsTable.requesterId] }
                        .singleOrNull()?.get(UsersTable.email)
                }

                PlatformRequestType.BLOCK_PARTNER -> {
                    PartnersTable.update({ PartnersTable.id eq partnerId }) {
                        it[status] = PartnerStatus.BLOCKED
                    }
                    systemEventRepository.logEvent(
                        type = SystemEventType.INFO,
                        userId = approverId,
                        partnerId = partnerId,
                        payload = "BLOCKED Partner"
                    )
                    null
                }

                PlatformRequestType.UNBLOCK_PARTNER -> {
                    PartnersTable.update({ PartnersTable.id eq partnerId }) {
                        it[status] = PartnerStatus.ACTIVE
                    }
                    systemEventRepository.logEvent(
                        type = SystemEventType.INFO,
                        userId = approverId,
                        partnerId = partnerId,
                        payload = "UNBLOCKED Partner"
                    )
                    null
                }
            }
        }

        // 2. Network: Send Email
        if (!emailToNotify.isNullOrBlank()) {
            try {
                emailService.sendEmail(
                    emailToNotify,
                    "LoyaltyLoop: Request Approved",
                    "Your request has been approved."
                )
            } catch (e: Exception) {
                // Log warning
            }
        }
    }

    suspend fun rejectRequest(requestId: String, approverId: String, reason: String) = dbQuery {
        val requestRow =
            PlatformRequestsTable.selectAll().where { PlatformRequestsTable.id eq requestId }
                .singleOrNull()
                ?: throw LoyaltyException(AppErrorCode.NOT_FOUND, "Request not found")

        // TargetPointId is nullable in table, but mostly present. Safe handling:
        val pointId = requestRow[PlatformRequestsTable.targetPointId]
        val partnerId = if (pointId != null) {
            TradingPointsTable.slice(TradingPointsTable.partnerId)
                .select { TradingPointsTable.id eq pointId }
                .singleOrNull()?.get(TradingPointsTable.partnerId)
        } else null

        PlatformRequestsTable.update({ PlatformRequestsTable.id eq requestId }) {
            it[status] = PlatformRequestStatus.REJECTED.name
            it[this.approverId] = approverId
            it[rejectReason] = reason
            it[updatedAt] = System.currentTimeMillis()
        }

        systemEventRepository.logEvent(
            type = SystemEventType.INFO, userId = approverId, partnerId = partnerId,
            payload = "REJECTED: ${requestRow[PlatformRequestsTable.type]}. Reason: $reason"
        )
    }

    // --- INVITES ---

    suspend fun generateInvite(role: UserRole, creatorId: String): String = dbQuery {
        val code = "ST-" + (100000..999999).random().toString()
        PlatformInvitesTable.insert {
            it[this.code] = code
            it[this.role] = role.name
            it[this.createdBy] = creatorId
            it[this.createdAt] = System.currentTimeMillis()
        }
        code
    }

    suspend fun validateInvite(code: String): UserRole? = dbQuery {
        PlatformInvitesTable.slice(PlatformInvitesTable.role)
            .select { PlatformInvitesTable.code eq code }
            .singleOrNull()?.get(PlatformInvitesTable.role)
            ?.let { UserRole.valueOf(it) }
    }

    // --- SYSTEM STAFF ---

    suspend fun createSystemStaff(userId: String, role: UserRole, defaultPinHash: String? = null) =
        dbQuery {
            if (!SystemStaffTable.select { SystemStaffTable.userId eq userId }.empty()) {
                throw LoyaltyException(
                    AppErrorCode.ALREADY_JOINED,
                    "User already has a platform role"
                )
            }

            SystemStaffTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[this.userId] = userId
                it[this.role] = role
                it[pinHash] = defaultPinHash
            }
            true
        }

    suspend fun hasAnyPlatformRole(userId: String): Boolean = dbQuery {
        !SystemStaffTable.select { SystemStaffTable.userId eq userId }.empty()
    }

    suspend fun hasPlatformRole(userId: String, role: UserRole): Boolean = dbQuery {
        !SystemStaffTable
            .select { (SystemStaffTable.userId eq userId) and (SystemStaffTable.role eq role) }
            .empty()
    }

    suspend fun isSuperAdmin(userId: String): Boolean = dbQuery {
        val isRole =
            !SystemStaffTable.select { (SystemStaffTable.userId eq userId) and (SystemStaffTable.role eq UserRole.PLATFORM_SUPER_ADMIN) }
                .empty()
        return@dbQuery isRole
    }

    suspend fun getSystemStaff(role: UserRole? = null): List<Employer> = dbQuery {
        val query = SystemStaffTable.innerJoin(UsersTable).selectAll()
        role?.let { query.andWhere { SystemStaffTable.role eq it } }

        query.map {
            Employer(
                id = it[SystemStaffTable.id],
                userId = it[UsersTable.id],
                name = "${it[UsersTable.firstName] ?: ""} ${it[UsersTable.lastName] ?: ""}".trim(),
                phone = it[UsersTable.phoneNumber],
                active = true,
                pointName = it[SystemStaffTable.role].name
            )
        }
    }

    suspend fun removeSystemStaff(staffId: String) = dbQuery {
        SystemStaffTable.deleteWhere { SystemStaffTable.id eq staffId }
    }

    suspend fun getSystemRole(userId: String): UserRole? = dbQuery {
        SystemStaffTable
            .selectAll()
            .where { SystemStaffTable.userId eq userId }
            .map { it[SystemStaffTable.role] }
            .singleOrNull()
    }

    suspend fun getSystemRoleByStaffId(staffId: String): UserRole? = dbQuery {
        SystemStaffTable
            .selectAll()
            .where { SystemStaffTable.id eq staffId }
            .map { it[SystemStaffTable.role] }
            .singleOrNull()
    }


    // --- UTILS ---

    private fun calculateEndDate(startDate: Long, duration: SubscriptionDuration): Long {
        val startInstant = Instant.ofEpochMilli(startDate).atZone(ZoneId.of("UTC"))
        val endInstant = when (duration) {
            SubscriptionDuration.DAY_1 -> startInstant.plusDays(1)
            SubscriptionDuration.DAY_3 -> startInstant.plusDays(3)
            SubscriptionDuration.WEEK_2 -> startInstant.plusWeeks(2)
            SubscriptionDuration.MONTH_1 -> startInstant.plusMonths(1)
            SubscriptionDuration.MONTH_3 -> startInstant.plusMonths(3)
            SubscriptionDuration.MONTH_6 -> startInstant.plusMonths(6)
            SubscriptionDuration.YEAR_1 -> startInstant.plusYears(1)
            SubscriptionDuration.YEAR_2 -> startInstant.plusYears(2)
            SubscriptionDuration.YEAR_3 -> startInstant.plusYears(3)
            SubscriptionDuration.YEAR_5 -> startInstant.plusYears(5)
        }
        return endInstant.toInstant().toEpochMilli()
    }
}
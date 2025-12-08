package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.PlatformRequestsTable
import io.loyaltyloop.server.database.tables.PlatformSubscriptionsTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.PlatformInvitesTable
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.service.SubscriptionWarningDto
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePlatformRequest
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.PlatformRequestDto
import io.loyaltyloop.shared.models.PlatformRequestStatus
import io.loyaltyloop.shared.models.PlatformRequestType
import io.loyaltyloop.shared.models.SubscriptionDto
import io.loyaltyloop.shared.models.SubscriptionDuration
import io.loyaltyloop.shared.models.SubscriptionType
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.Employer
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class PlatformRepository {
    
    private val systemEventRepository = SystemEventRepository()

    suspend fun getExpiringSubscriptions(): List<SubscriptionWarningDto> = dbQuery {
        val now = System.currentTimeMillis()
        val warningThreshold = now + (3 * 24 * 60 * 60 * 1000L) // 3 days

        val allSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable) // Join Partners for name
            .select {
                (PlatformSubscriptionsTable.isActive eq true) and
                (PlatformSubscriptionsTable.endDate.isNotNull())
            }
            .map {
                Triple(
                    it[TradingPointsTable.id], // PointId
                    it[PlatformSubscriptionsTable.endDate]!!, // EndDate
                    it // Row
                )
            }

        // Group by PointId and find the MAX end date for each point
        val maxEndDates = allSubs
            .groupBy { it.first }
            .mapValues { (_, subs) -> subs.maxOf { it.second } }

        // Filter: Keep only points where MAX end date is expiring soon
        allSubs
            .filter { (pointId, endDate, _) ->
                val maxDate = maxEndDates[pointId] ?: return@filter false
                
                // Only consider this subscription if it IS the one with the max date (or close to it)
                // AND that max date is expiring.
                // If maxDate > warningThreshold, it means point is safe (renewed).
                
                if (maxDate > warningThreshold) return@filter false // Point is safe
                if (maxDate < now) return@filter false // Already expired (handled by job)
                
                // If we are here, the point is expiring soon.
                // Return true for the subscription that matches the max date (to avoid duplicates)
                endDate == maxDate
            }
            .map { (_, _, row) ->
                SubscriptionWarningDto(
                    partnerId = row[TradingPointsTable.partnerId],
                    partnerName = row[PartnersTable.businessName], // Added
                    pointId = row[PlatformSubscriptionsTable.pointId],
                    pointName = row[TradingPointsTable.name],
                    endDate = row[PlatformSubscriptionsTable.endDate]!!
                )
            }
    }

    suspend fun createRequest(requesterId: String, request: CreatePlatformRequest): String = dbQuery {
        val targetPointId = request.targetPointId

        // Lookup PartnerID from PointID
        val partnerId = TradingPointsTable
            .select { TradingPointsTable.id eq targetPointId }
            .map { it[TradingPointsTable.partnerId] }
            .singleOrNull()

        if (partnerId.isNullOrBlank()) {
            throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Invalid Point ID (Point not found)")
        }

        // 1. Check Trial Eligibility
        if (request.isTrial) {
            // Check if THIS POINT has used trial? (Per user request: only POINT)
            
            val hasUsedTrial = PlatformSubscriptionsTable
                .select { 
                    (PlatformSubscriptionsTable.pointId eq targetPointId) and 
                    (PlatformSubscriptionsTable.isTrial eq true) 
                }
                .count() > 0
            
            if (hasUsedTrial) {
                throw LoyaltyException(AppErrorCode.TRIAL_ALREADY_USED, "Point has already used a trial period.")
            }
        }

        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

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
            payload = "Request Created by ${getInitiatorInfo(requesterId)}: ${request.type} for Point $targetPointId"
        )
        newId
    }

    suspend fun getRequests(
        status: PlatformRequestStatus? = null,
        requesterId: String? = null,
        targetPartnerId: String? = null
    ): List<PlatformRequestDto> = dbQuery {
        val query = PlatformRequestsTable
            .join(UsersTable, JoinType.LEFT, PlatformRequestsTable.requesterId, UsersTable.id) // Use LEFT JOIN
            .join(TradingPointsTable, JoinType.INNER, PlatformRequestsTable.targetPointId, TradingPointsTable.id)
            .join(PartnersTable, JoinType.INNER, TradingPointsTable.partnerId, PartnersTable.id)
            .selectAll()

        status?.let { query.andWhere { PlatformRequestsTable.status eq it.name } }
        requesterId?.let { query.andWhere { PlatformRequestsTable.requesterId eq it } }
        targetPartnerId?.let { query.andWhere { PartnersTable.id eq it } }

        query.orderBy(PlatformRequestsTable.createdAt to SortOrder.DESC)
            .map { row ->
                // Optional join for approver name? For now keeping it simple
                val approverId = row[PlatformRequestsTable.approverId]
                
                PlatformRequestDto(
                    id = row[PlatformRequestsTable.id],
                    type = PlatformRequestType.valueOf(row[PlatformRequestsTable.type]),
                    status = PlatformRequestStatus.valueOf(row[PlatformRequestsTable.status]),
                    requesterId = row[PlatformRequestsTable.requesterId],
                    requesterName = ("${row[UsersTable.firstName] ?: ""} ${row[UsersTable.lastName] ?: ""}").trim().ifBlank { "Unknown" },
                    requesterPhone = row[UsersTable.phoneNumber], // Added
                    approverId = approverId,
                    approverName = approverId?.apply { getInitiatorInfo(approverId) },
                    createdAt = row[PlatformRequestsTable.createdAt],
                    updatedAt = row[PlatformRequestsTable.updatedAt],
                    targetPartnerId = row[PartnersTable.id], // Fetched from join
                    targetPartnerName = row[PartnersTable.businessName],
                    targetPointId = row[PlatformRequestsTable.targetPointId],
                    targetPointName = row[TradingPointsTable.name],
                    currency = row[TradingPointsTable.currency], // Added
                    amount = row[PlatformRequestsTable.amount],
                    duration = row[PlatformRequestsTable.duration]?.let { SubscriptionDuration.valueOf(it) },
                    isTrial = row[PlatformRequestsTable.isTrial],
                    blockReason = row[PlatformRequestsTable.blockReason],
                    rejectReason = row[PlatformRequestsTable.rejectReason]
                )
            }
    }

    // Ideally inject EmailService, but for now using simplistic approach or global
    // To properly send email from here, we should inject EmailService into PlatformRepository or move logic to Service layer.
    // For now, I will assume we can pass it or use a temporary solution.
    // Better approach: Return a result DTO and let the Route/Service handle email. 
    // But to keep it simple as per request:
    
    suspend fun approveRequest(requestId: String, approverId: String, emailService: io.loyaltyloop.server.service.email.EmailService) = dbQuery {
        val requestRow = PlatformRequestsTable.select { PlatformRequestsTable.id eq requestId }.singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.NOT_FOUND, "Request not found")

        val currentStatus = PlatformRequestStatus.valueOf(requestRow[PlatformRequestsTable.status])
        if (currentStatus != PlatformRequestStatus.PENDING) {
            throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Request is not pending")
        }

        val type = PlatformRequestType.valueOf(requestRow[PlatformRequestsTable.type])
        val pointId = requestRow[PlatformRequestsTable.targetPointId] 
            ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Target Point ID missing")

        // Partner ID derived from point (which is mandatory now)
        val partnerRow = TradingPointsTable
            .join(PartnersTable, JoinType.INNER, TradingPointsTable.partnerId, PartnersTable.id)
            .select { TradingPointsTable.id eq pointId }
            .map { it[TradingPointsTable.partnerId] to it[PartnersTable.status] }
            .single()

        val partnerId = partnerRow.first
        val partnerStatus = partnerRow.second

        if (type == PlatformRequestType.ACTIVATE_POINT) {
            if (partnerStatus == PartnerStatus.PENDING) {
                 throw LoyaltyException(AppErrorCode.PARTNER_ON_REVIEW, "Partner is under review. Approve partner first.")
            }
            if (partnerStatus == PartnerStatus.BLOCKED) {
                 throw LoyaltyException(AppErrorCode.PARTNER_BLOCKED, "Partner is blocked. Unblock partner first.")
            }
        }

        val now = System.currentTimeMillis()

        // Update Request
        PlatformRequestsTable.update({ PlatformRequestsTable.id eq requestId }) {
            it[status] = PlatformRequestStatus.APPROVED.name
            it[this.approverId] = approverId
            it[updatedAt] = now
        }

        // Execute Logic
        when (type) {
            PlatformRequestType.ACTIVATE_POINT -> {
                // 1. Deactivate any existing active subscriptions for this point
                PlatformSubscriptionsTable.update({ (PlatformSubscriptionsTable.pointId eq pointId) and (PlatformSubscriptionsTable.isActive eq true) }) {
                    it[isActive] = false
                }

                // 2. Create Subscription
                val durationName = requestRow[PlatformRequestsTable.duration] ?: "MONTH_1"
                val duration = SubscriptionDuration.valueOf(durationName)
                val isTrial = requestRow[PlatformRequestsTable.isTrial]
                
                val endDate = if (isTrial) {
                     // Fixed 14 days trial
                     Instant.ofEpochMilli(now).plus(14, ChronoUnit.DAYS).toEpochMilli()
                } else {
                     calculateEndDate(now, duration)
                }
                
                PlatformSubscriptionsTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[this.pointId] = pointId
                    it[this.requesterId] = requestRow[PlatformRequestsTable.requesterId] // Save Manager ID
                    it[this.type] = SubscriptionType.FIXED_TERM.name
                    it[startDate] = now
                    it[this.endDate] = endDate
                    it[amount] = requestRow[PlatformRequestsTable.amount] ?: 0.0
                    it[this.isTrial] = isTrial
                    it[isActive] = true
                    it[createdAt] = now
                }

                // 2. Activate Point
                TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
                    it[isActive] = true
                }
                
                // 3. Activate Partner if needed
                PartnersTable.update({ PartnersTable.id eq partnerId }) {
                    it[status] = PartnerStatus.ACTIVE
                }

                systemEventRepository.logEvent(
                    type = SystemEventType.INFO,
                    userId = approverId,
                    partnerId = partnerId,
                    payload = "Request APPROVED by ${getInitiatorInfo(approverId)}: ACTIVATE_POINT for Point $pointId"
                )

                // 4. Notify Manager (Requester)
                val requesterId = requestRow[PlatformRequestsTable.requesterId]
                val requesterEmail = UsersTable.select { UsersTable.id eq requesterId }
                    .map { it[UsersTable.email] }
                    .singleOrNull()
                
                if (!requesterEmail.isNullOrBlank()) {
                    emailService.sendEmail(
                        requesterEmail, 
                        "LoyaltyLoop: Request Approved", 
                        "Your request to activate point has been APPROVED. Point is now active."
                    )
                }
            }
            PlatformRequestType.BLOCK_PARTNER -> {
                PartnersTable.update({ PartnersTable.id eq partnerId }) {
                    it[status] = PartnerStatus.BLOCKED
                }
                
                systemEventRepository.logEvent(
                    type = SystemEventType.INFO,
                    userId = approverId,
                    partnerId = partnerId,
                    payload = "Partner BLOCKED by ${getInitiatorInfo(approverId)}"
                )

                // Removed: Deactivate all trading points (Soft Ban logic)
                // Points remain active (with subscriptions ticking), but partner is blocked.
                // TransactionService and Search must check PartnerStatus.
            }
            PlatformRequestType.UNBLOCK_PARTNER -> {
                PartnersTable.update({ PartnersTable.id eq partnerId }) {
                    it[status] = PartnerStatus.ACTIVE
                }
                systemEventRepository.logEvent(
                    type = SystemEventType.INFO,
                    userId = approverId,
                    partnerId = partnerId,
                    payload = "Partner UNBLOCKED by ${getInitiatorInfo(approverId)}"
                )
            }
        }
    }

    suspend fun rejectRequest(requestId: String, approverId: String, reason: String) = dbQuery {
        val requestRow = PlatformRequestsTable.select { PlatformRequestsTable.id eq requestId }.singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.NOT_FOUND, "Request not found")

        val pointId = requestRow[PlatformRequestsTable.targetPointId] ?: ""
        val partnerId = if (pointId.isNotBlank()) {
            TradingPointsTable
                .select { TradingPointsTable.id.eq(pointId) }
                .map { it[TradingPointsTable.partnerId] }
                .singleOrNull()
        } else {
            null
        } ?: throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Associated Partner not found for request")

        PlatformRequestsTable.update({ PlatformRequestsTable.id eq requestId }) {
            it[status] = PlatformRequestStatus.REJECTED.name
            it[this.approverId] = approverId
            it[rejectReason] = reason
            it[updatedAt] = System.currentTimeMillis()
        }

        systemEventRepository.logEvent(
            type = SystemEventType.INFO,
            userId = approverId,
            partnerId = partnerId,
            payload = "Request REJECTED by ${getInitiatorInfo(approverId)}: ${requestRow[PlatformRequestsTable.type]} for Point $pointId. Reason: $reason"
        )
    }

    private fun getInitiatorInfo(userId: String): String {
        return UsersTable
            .join(SystemStaffTable, JoinType.LEFT, UsersTable.id, SystemStaffTable.userId)
            .select { UsersTable.id.eq(userId) }
            .map { row ->
                val role = row.getOrNull(SystemStaffTable.role)?.name
                    ?: if (row[UsersTable.isSuperAdmin]) "PLATFORM_SUPER_ADMIN" else "User"
                val name = "${row[UsersTable.firstName] ?: ""} ${row[UsersTable.lastName] ?: ""}".trim()
                "$role $name"
            }
            .singleOrNull() ?: "Unknown (ID: $userId)"
    }

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

    // --- INVITE & STAFF ---

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
        PlatformInvitesTable
            .select { PlatformInvitesTable.code eq code }
            .map { UserRole.valueOf(it[PlatformInvitesTable.role]) }
            .singleOrNull()
    }

    suspend fun addSystemStaff(userId: String, role: UserRole) = dbQuery {
        SystemStaffTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.userId] = userId
            it[this.role] = role
        }
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
                pointName = it[SystemStaffTable.role].name // Using pointName as Role description
            )
        }
    }

    suspend fun removeSystemStaff(staffId: String) = dbQuery {
        SystemStaffTable.deleteWhere { SystemStaffTable.id eq staffId }
    }

    suspend fun getSystemRole(userId: String): UserRole? = dbQuery {
        SystemStaffTable
            .select { SystemStaffTable.userId eq userId }
            .map { it[SystemStaffTable.role] }
            .singleOrNull()
    }

    suspend fun getSystemRoleByStaffId(staffId: String): UserRole? = dbQuery {
        SystemStaffTable
            .select { SystemStaffTable.id eq staffId }
            .map { it[SystemStaffTable.role] }
            .singleOrNull()
    }
}

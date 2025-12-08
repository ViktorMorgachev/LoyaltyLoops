package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnerManagersTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.PlatformSubscriptionsTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.or
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.service.sms.ConsoleSmsService
import io.loyaltyloop.server.service.email.ConsoleEmailService
import io.loyaltyloop.server.service.email.EmailService
import org.jetbrains.exposed.sql.JoinType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import io.loyaltyloop.server.i18n.ServerResources
import io.loyaltyloop.server.models.SystemEventType
import kotlinx.coroutines.CoroutineScope


data class SubscriptionWarningDto(
    val partnerId: String,
    val partnerName: String, // Added
    val pointId: String,
    val pointName: String,
    val endDate: Long
)

class LoyaltyEngineService {

    private val logger = LoggerFactory.getLogger("LoyaltyEngine")
    // Ideally inject these, but for background job simplistic instantiation is fine or use global
    private val systemEventRepository = SystemEventRepository()
    private val smsService = ConsoleSmsService() // Placeholder for notifications
    private val emailService = ConsoleEmailService() // Placeholder

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            logger.info("Loyalty Engine started")
            delay(10000) 
            
            while (isActive) {
                try {
                    runExpirationCycle()
                    runSubscriptionCheck()
                } catch (e: Exception) {
                    logger.error("Error in engine cycle", e)
                }
                delay(24.hours) 
            }
        }
    }

    private suspend fun runExpirationCycle() = dbQuery {
        // ... (existing expiration logic)
        logger.info("Running expiration cycle...")
        val now = System.currentTimeMillis()

        val rules = PartnersTable
            .selectAll().where { PartnersTable.burnBonusesDays.isNotNull() }.associate {
                it[PartnersTable.id] to it[PartnersTable.burnBonusesDays]!!
            }

        var expiredCount = 0

        rules.forEach { (partnerId, days) ->
            val threshold = now - (days * 24 * 60 * 60 * 1000L)

            val cards = LoyaltyCardTable.select {
                (LoyaltyCardTable.partnerId eq partnerId) and
                (LoyaltyCardTable.lastActivityAt less threshold) and
                (LoyaltyCardTable.balance greater 0.0)
            }.map { 
                Triple(it[LoyaltyCardTable.id], it[LoyaltyCardTable.balance], it[LoyaltyCardTable.userId])
            }

            cards.forEach { (cardId, balance, cardUserId) ->
                LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
                    it[this.balance] = 0.0
                    it[lastActivityAt] = now 
                }

                TransactionsHistoryTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[userId] = cardUserId
                    it[tradingPointId] = "SYSTEM_EXPIRATION"
                    it[cashierId] = "SYSTEM"
                    it[type] = "EXPIRATION"
                    it[amount] = 0.0
                    it[pointsDelta] = -balance
                    it[visitsDelta] = 0
                    it[timestamp] = now
                }
                expiredCount++
            }
        }
        
        if (expiredCount > 0) {
            logger.info("Expiration cycle completed. Expired $expiredCount cards.")
        }
    }

    suspend fun runSubscriptionCheck() = dbQuery {
        logger.info("Running subscription check...")
        val now = System.currentTimeMillis()
        
        // 1. Find EXPIRED subscriptions (FIXED_TERM)
        val expiredSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable) // Join to get partnerId from Point
            .select {
            (PlatformSubscriptionsTable.isActive eq true) and 
            (PlatformSubscriptionsTable.endDate.isNotNull()) and 
            (PlatformSubscriptionsTable.endDate less now)
        }.map { 
            Triple(it[PlatformSubscriptionsTable.id], it[TradingPointsTable.partnerId], it[PlatformSubscriptionsTable.pointId])
        }

        expiredSubs.forEach { (subId, partnerId, pointId) ->
            logger.warn("Subscription expired for Point $pointId (Partner $partnerId, Sub: $subId). Deactivating...")
            
            // Deactivate Subscription
            PlatformSubscriptionsTable.update({ PlatformSubscriptionsTable.id eq subId }) {
                it[isActive] = false
            }

            // Deactivate Specific Point (pointId is mandatory)
            TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
                it[isActive] = false
            }

            // Notify (Log event)
            systemEventRepository.logEvent(
                type = SystemEventType.INFO,
                userId = "SYSTEM",
                userPhone = "SYSTEM",
                partnerId = partnerId,
                payload = "Subscription EXPIRED for Point $pointId. Deactivated."
            )
        }

        // 2. Find WARNING subscriptions (Expires in <= 3 days)
        val warningThreshold = now + (3 * 24 * 60 * 60 * 1000L)
        val allWarningSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable) // Join Partners to get business name
            .select {
            (PlatformSubscriptionsTable.isActive eq true) and
            (TradingPointsTable.isActive eq true) and // Check Point is Active
            (PlatformSubscriptionsTable.endDate.isNotNull()) and
            (PlatformSubscriptionsTable.endDate less warningThreshold) and
            (PlatformSubscriptionsTable.endDate greater now)
        }.map {
            SubscriptionWarningDto(
                partnerId = it[TradingPointsTable.partnerId],
                partnerName = it[PartnersTable.businessName],
                pointId = it[PlatformSubscriptionsTable.pointId],
                pointName = it[TradingPointsTable.name],
                endDate = it[PlatformSubscriptionsTable.endDate]!!
            )
        }

        // Filter duplicates: Group by PointID, take the subscription with MAX endDate
        val warningSubs = allWarningSubs
            .groupBy { it.pointId }
            .map { (_, subs) -> 
                // We assume the valid subscription is the one that extends furthest.
                // If I have one expiring tomorrow and one next month, the point is safe until next month.
                // But here our query only picked subs expiring < 3 days.
                // So all of them are expiring soon. We take the latest of them.
                subs.maxByOrNull { it.endDate }!!
            }

        warningSubs.forEach { sub ->
            val timeLeft = sub.endDate - now
            val daysLeft = timeLeft / (24 * 60 * 60 * 1000.0)

            // Logic:
            // If roughly 1 day left (0.0 < daysLeft <= 1.2): Send URGENT (Email + SMS)
            // If roughly 3 days left (1.2 < daysLeft <= 3.2): Send WARNING (Email)
            // Note: This logic depends on job frequency. Assuming daily job.
            
            // Or strictly:
            // 3 Day Warning: 1.2 < days <= 3.2
            // 1 Day Warning: days <= 1.2
            
            var sendSms = false
            var subjectKey = ""
            var bodyKey = ""
            var eventPayload = ""
            val isUrgent: Boolean
            
            if (daysLeft <= 1.2) {
                // Critical (approx 1 day or less)
                isUrgent = true
                sendSms = true
                subjectKey = "sub_expiring_1day_subject"
                bodyKey = "sub_expiring_1day_body"
                eventPayload = "CRITICAL: Subscription expires in < 24h for Point ${sub.pointId}."
            } else if (daysLeft > 1.2 && daysLeft <= 3.2) {
                // Warning (approx 3 days)
                isUrgent = false
                sendSms = false // Only Email
                subjectKey = "sub_expiring_subject"
                bodyKey = "sub_expiring_body"
                eventPayload = "WARNING: Subscription expires in 3 days for Point ${sub.pointId}."
            } else {
                // In between or too far
                return@forEach
            }
            
            logger.info("Processing notification for Point ${sub.pointId}. Days left: $daysLeft. Urgent: $sendSms")
            
            systemEventRepository.logEvent(
                type = SystemEventType.INFO,
                userId = "SYSTEM",
                userPhone = "SYSTEM",
                partnerId = sub.partnerId,
                payload = eventPayload
            )

            // Notify Owner
            val ownerInfo = PartnersTable
                .join(UsersTable, JoinType.INNER, PartnersTable.ownerId, UsersTable.id)
                .select { PartnersTable.id eq sub.partnerId }
                .map { Triple(it[UsersTable.phoneNumber], it[UsersTable.email], it[UsersTable.language]) }
                .singleOrNull()

            if (ownerInfo != null) {
                val (phone, email, lang) = ownerInfo
                val dateStr = java.time.Instant.ofEpochMilli(sub.endDate).toString()
                
                val args = mapOf("pointName" to sub.pointName, "date" to dateStr)
                
                val message = ServerResources.get(lang, bodyKey, args)
                val subject = ServerResources.get(lang, subjectKey)
                
                if (sendSms) {
                     smsService.sendSms(phone, message)
                     systemEventRepository.logEvent(
                        type = SystemEventType.INFO,
                        userId = "SYSTEM",
                        partnerId = sub.partnerId,
                        payload = "Notification SENT (SMS) to owner ($phone)"
                     )
                }
                
                if (!email.isNullOrBlank()) {
                    emailService.sendEmail(email, subject, message)
                    systemEventRepository.logEvent(
                        type = SystemEventType.INFO,
                        userId = "SYSTEM",
                        partnerId = sub.partnerId,
                        payload = "Notification SENT (EMAIL) to owner ($email)"
                     )
                }
            }

            // --- Notify Partner Managers ---
            val partnerManagers = PartnerManagersTable
                .innerJoin(UsersTable)
                .select { (PartnerManagersTable.partnerId eq sub.partnerId) and (PartnerManagersTable.isActive eq true) }
                .map { Triple(it[UsersTable.email], it[UsersTable.phoneNumber], it[UsersTable.language]) }

            partnerManagers.forEach { (pmEmail, pmPhone, pmLang) ->
                 val dateStr = java.time.Instant.ofEpochMilli(sub.endDate).toString()
                 val args = mapOf("pointName" to sub.pointName, "partnerName" to sub.partnerName, "date" to dateStr)

                 // Reuse Manager Template
                 val managerBodyKey = if (isUrgent) "sub_expiring_1day_manager_body" else "sub_expiring_manager_body"
                 
                 val message = ServerResources.get(pmLang, managerBodyKey, args)
                 val subject = ServerResources.get(pmLang, subjectKey)
                 
                 if (!pmEmail.isNullOrBlank()) {
                     emailService.sendEmail(pmEmail, subject, message)
                     systemEventRepository.logEvent(
                        type = SystemEventType.INFO,
                        userId = "SYSTEM",
                        partnerId = sub.partnerId,
                        payload = "Notification SENT (EMAIL) to Partner Manager ($pmEmail)"
                     )
                 }
            }

            // --- Notify Manager (Requester) ---
            val requesterId = PlatformSubscriptionsTable
                .select { (PlatformSubscriptionsTable.pointId eq sub.pointId) and (PlatformSubscriptionsTable.isActive eq true) }
                .map { it[PlatformSubscriptionsTable.requesterId] }
                .singleOrNull()

            if (!requesterId.isNullOrBlank()) {
                val managerInfo = UsersTable.select { UsersTable.id eq requesterId }
                    .map { it[UsersTable.email] to it[UsersTable.phoneNumber] }
                    .singleOrNull()
                
                if (managerInfo != null) {
                    val (mgrEmail, mgrPhone) = managerInfo
                    val dateStr = java.time.Instant.ofEpochMilli(sub.endDate).toString()
                    val args = mapOf("pointName" to sub.pointName, "partnerName" to sub.partnerName, "date" to dateStr)
                    
                    // Determine Manager Template Keys
                    val managerBodyKey = if (isUrgent) "sub_expiring_1day_manager_body" else "sub_expiring_manager_body"
                    
                    // Use default EN or RU for manager if lang not known
                    val message = ServerResources.get("ru", managerBodyKey, args) 
                    val subject = ServerResources.get("ru", subjectKey)

                    // EXCEPTION: Do NOT send SMS to Manager for 1-day warning (Email only)
                    // sendSms logic is only for Partner Owner in this context
                    
                    if (!mgrEmail.isNullOrBlank()) {
                        emailService.sendEmail(mgrEmail, subject, message)
                        systemEventRepository.logEvent(
                            type = SystemEventType.INFO,
                            userId = "SYSTEM",
                            partnerId = sub.partnerId,
                            payload = "Notification SENT (EMAIL) to Manager ($mgrEmail)"
                        )
                    }
                }
            }
        }
        
        // Notify Platform Admins (Summary of ALL expiring < 3 days)
        if (warningSubs.isNotEmpty()) {
            notifyPlatformAdmins(warningSubs)
        }
    }

    private suspend fun notifyPlatformAdmins(subs: List<SubscriptionWarningDto>) = dbQuery {
         val admins = UsersTable
             .join(SystemStaffTable, JoinType.LEFT, UsersTable.id, SystemStaffTable.userId)
             .select {
                 (UsersTable.isSuperAdmin eq true) or
                         (SystemStaffTable.role eq UserRole.PLATFORM_SUPER_MANAGER)
             }.mapNotNull { it[UsersTable.email] }
             .filter { it.isNotBlank() }
            .distinct()

         if (admins.isEmpty()) return@dbQuery

         val sb = StringBuilder("Critical: The following subscriptions are expiring soon:\n\n")
         subs.forEach { sub ->
             sb.append("- Point: ${sub.pointName} (ID: ${sub.pointId})\n")
             sb.append("  Partner ID: ${sub.partnerId}\n")
             sb.append("  Expires: ${java.time.Instant.ofEpochMilli(sub.endDate)}\n\n")
         }
         
         admins.forEach { email ->
             emailService.sendEmail(email, "LoyaltyLoop: Expiring Subscriptions Report", sb.toString())
             
             systemEventRepository.logEvent(
                type = SystemEventType.INFO,
                userId = "SYSTEM",
                partnerId = "PLATFORM", // General platform event
                payload = "Notification SENT (EMAIL SUMMARY) to Admin ($email)"
             )
         }
    }
}

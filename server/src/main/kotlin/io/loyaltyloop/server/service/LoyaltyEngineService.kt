package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnerStaffTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.PlatformSubscriptionsTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.i18n.ServerResources
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.or
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.service.email.EmailService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.RefreshTokenRepository
import io.loyaltyloop.server.service.email.EmailTemplate
import io.loyaltyloop.server.service.email.EmailTemplateService
import io.loyaltyloop.server.service.sms.SmsService
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUserDto
import io.loyaltyloop.shared.models.TransactionTypeHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.exposed.sql.ResultRow
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

// TODO checked
class LoyaltyEngineService(
    val smsService: SmsService,
    val emailService: EmailService,
    val emailTemplateService: EmailTemplateService,
    val systemEventRepository: SystemEventRepository,
    val refreshTokenRepository: RefreshTokenRepository,
) {

    private val logger = LoggerFactory.getLogger("LoyaltyEngine")

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    fun start() {
        logger.info("Loyalty Engine started")
        scope.launch {
            delay(10000) // Warmup

            while (isActive) {
                try {
                    runExpirationCycle()
                } catch (e: Exception) {
                    logger.error("Error in runExpirationCycle cycle", e)
                }
                // Запускаем раз в сутки (или раз в час, если нужно точнее)
                delay(24.hours)
            }
        }

        scope.launch {

            delay(10000) // Warmup

            while (isActive) {
                try {
                    runSubscriptionCheck()
                } catch (e: Exception) {
                    logger.error("Error in runSubscriptionCheck cycle", e)
                }
                // Запускаем раз в сутки (или раз в час, если нужно точнее)
                delay(24.hours)
            }
        }

        scope.launch {
            delay(10000) // Warmup
            while (isActive) {
                try {
                    runExpiredTokens()
                } catch (e: Exception) {
                    logger.error("Error in runExpiredTokens cycle", e)
                }
                delay(1.hours)
            }
        }

    }

    private data class PartnerExpirationRule(
        val partnerId: UUID,
        val burnDays: Int?,
        val downgradeDays: Int?,
        val baseCurrency: String
    )

    private suspend fun runExpiredTokens(){
        refreshTokenRepository.cleanupExpiredTokens()
    }

    private suspend fun runExpirationCycle() = dbQuery {
        logger.info("Running expiration & downgrade cycle...")
        val now = nowUtc()

        val partnersRules = PartnersTable
            .slice(
                PartnersTable.id,
                PartnersTable.burnBonusesDays,
                PartnersTable.downgradeTierDays,
                PartnersTable.baseCurrency
            )
            .select {
                (PartnersTable.burnBonusesDays.isNotNull()) or
                        (PartnersTable.downgradeTierDays.isNotNull())
            }
            .map { row ->
                PartnerExpirationRule(
                    partnerId = row[PartnersTable.id].value,
                    burnDays = row[PartnersTable.burnBonusesDays],
                    downgradeDays = row[PartnersTable.downgradeTierDays],
                    baseCurrency = row[PartnersTable.baseCurrency]
                )
            }

        var burnedCount = 0
        var downgradedCount = 0

        partnersRules.forEach { rule ->

            if (rule.burnDays != null) {
                val burnThreshold = now.minusDays(rule.burnDays.toLong())

                val cardsToBurn = LoyaltyCardsTable
                    .select {
                        (LoyaltyCardsTable.partner eq rule.partnerId) and
                                (LoyaltyCardsTable.lastActivityAt less burnThreshold) and
                                (LoyaltyCardsTable.balance greater BigDecimal.ZERO)
                    }
                    .toList()

                cardsToBurn.forEach { row ->
                    val cardId = row[LoyaltyCardsTable.id]
                    val currentBalance = row[LoyaltyCardsTable.balance]
                    val userId = row[LoyaltyCardsTable.user]

                    LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardId }) {
                        it[this.balance] = BigDecimal.ZERO
                        it[this.visitsCount] = 0
                    }

                    // 2. Пишем в историю
                    TransactionsHistoryTable.insert {
                        it[user] = userId
                        it[partner] = rule.partnerId
                        it[type] = TransactionTypeHistory.EXPIRATION
                        it[amount] = BigDecimal.ZERO
                        it[pointsDelta] = currentBalance.negate()
                        it[visitsDelta] = 0
                        it[exchangeRateSnapshot] = BigDecimal.ONE
                        it[currency] = rule.baseCurrency
                        it[createdAt] = now
                    }
                    burnedCount++
                }
            }

            if (rule.downgradeDays != null) {
                val downgradeThreshold = now.minusDays(rule.downgradeDays.toLong())

                // Ищем карты: (Этого партнера) И (Давно не был) И (Уровень выше 1)
                // Если уровень уже 1 (Start), понижать некуда.
                val cardsToDowngrade = LoyaltyCardsTable
                    .select {
                        (LoyaltyCardsTable.partner eq rule.partnerId) and
                                (LoyaltyCardsTable.lastActivityAt less downgradeThreshold) and
                                (LoyaltyCardsTable.tierLevel greater 1)
                    }
                    .toList()

                if (cardsToDowngrade.isNotEmpty()) {
                    // Нам нужно знать пороги уровней этого партнера, чтобы правильно срезать TotalSpent
                    val partnerTiers = LoyaltyTiersTable
                        .select { LoyaltyTiersTable.partner eq rule.partnerId }
                        .orderBy(LoyaltyTiersTable.levelIndex).associate {
                            it[LoyaltyTiersTable.levelIndex] to it[LoyaltyTiersTable.threshold]
                        } // Map<LevelIndex, Threshold>

                    cardsToDowngrade.forEach { row ->
                        val cardId = row[LoyaltyCardsTable.id]
                        val currentLevel = row[LoyaltyCardsTable.tierLevel]
                        val totalVisits = row[LoyaltyCardsTable.visitsCount]
                        val userId = row[LoyaltyCardsTable.user]

                        val targetLevel = currentLevel - 1
                        val targetVisits = (totalVisits - 1).coerceAtLeast(0)

                        // Получаем порог (цену) целевого уровня
                        // Например, для Silver (2) порог 5000.
                        // Если у юзера было 9000 (Gold), мы срежем ему LTV до 5000.
                        val targetThreshold = partnerTiers[targetLevel] ?: BigDecimal.ZERO

                        // 1. Понижаем уровень и срезаем LTV
                        LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardId }) {
                            it[tierLevel] = targetLevel
                            it[totalSpent] = targetThreshold
                            it[visitsCount] = targetVisits
                        }

                        TransactionsHistoryTable.insert {
                            it[user] = userId
                            it[partner] = rule.partnerId
                            it[type] = TransactionTypeHistory.TIER_DOWNGRADE
                            it[amount] = BigDecimal.ZERO
                            it[pointsDelta] = BigDecimal.ZERO
                            it[visitsDelta] = -1
                            it[exchangeRateSnapshot] = BigDecimal.ONE
                            it[currency] = rule.baseCurrency
                            it[createdAt] = now
                        }

                        systemEventRepository.logEvent(
                            type = SystemEventType.TIER_CHANGE,
                            userId = userId.value.toString(),
                            partnerId = rule.partnerId.toString(),
                            payload = "Inactive Downgrade: Level $currentLevel -> $targetLevel"
                        )
                        downgradedCount++
                    }
                }
            }
        }

        if (burnedCount > 0 || downgradedCount > 0) {
            logger.info("Cycle finished. Burned: $burnedCount cards, Downgraded: $downgradedCount cards.")
        }
    }

    suspend fun runSubscriptionCheck() = dbQuery {
        logger.info("Running subscription check...")
        val now = nowUtc()
        val in3Days = now.plusDays(3)
        val in1Day = now.plusDays(1)


        // А. WARNING (3 дня осталось, еще не отправляли)
        val warningSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable)
            .select {
                (PlatformSubscriptionsTable.isActive eq true) and
                        (PlatformSubscriptionsTable.endDate.isNotNull()) and
                        (PlatformSubscriptionsTable.endDate lessEq in3Days) and
                        (PlatformSubscriptionsTable.endDate greater in1Day) and
                        (PlatformSubscriptionsTable.warningSentAt.isNull())
            }
            .toList()

        warningSubs.forEach { row ->
            sendNotifications(row, isUrgent = false)

            PlatformSubscriptionsTable.update({ PlatformSubscriptionsTable.id eq row[PlatformSubscriptionsTable.id] }) {
                it[warningSentAt] = now
                it[this.updatedAt] = updatedAt
            }
        }

        val criticalSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable)
            .select {
                (PlatformSubscriptionsTable.isActive eq true) and
                        (PlatformSubscriptionsTable.endDate.isNotNull()) and
                        (PlatformSubscriptionsTable.endDate lessEq in1Day) and
                        (PlatformSubscriptionsTable.endDate greater now) and
                        (PlatformSubscriptionsTable.criticalSentAt.isNull())
            }
            .toList()

        criticalSubs.forEach { row ->
            sendNotifications(row, isUrgent = true)

            PlatformSubscriptionsTable.update({ PlatformSubscriptionsTable.id eq row[PlatformSubscriptionsTable.id] }) {
                it[criticalSentAt] = now
                it[this.updatedAt] = now
            }
        }

        val expiredSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .select {
                (PlatformSubscriptionsTable.isActive eq true) and
                        (PlatformSubscriptionsTable.endDate.isNotNull()) and
                        (PlatformSubscriptionsTable.endDate lessEq now)
            }
            .toList()

        expiredSubs.forEach { row ->
            val subId = row[PlatformSubscriptionsTable.id]
            val pointId = row[TradingPointsTable.id].value
            val partnerId = row[TradingPointsTable.partner].value

            PlatformSubscriptionsTable.update({ PlatformSubscriptionsTable.id eq subId }) {
                it[isActive] = false
                it[this.updatedAt] = now
            }

            // Деактивируем точку (согласно документации)
            TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
                it[isActive] = false
                it[this.updatedAt] = now
                it[isTemporarilyPaused] = true
            }

            systemEventRepository.logEvent(
                type = SystemEventType.SUBSCRIPTION_EXPIRED,
                userId = "SYSTEM",
                partnerId = partnerId.toString(),
                payload = "Subscription EXPIRED for Point $pointId. Deactivated."
            )
        }

        // Г. Уведомление Админам (Сводный отчет)
        if (warningSubs.isNotEmpty() || criticalSubs.isNotEmpty()) {
            notifyPlatformAdmins(warningSubs,  criticalSubs)
        }

    }

    // --- NOTIFICATION LOGIC ---

    private suspend fun sendNotifications(row: ResultRow, isUrgent: Boolean) {
        // 1. Подготовка данных
        val pointName = row[TradingPointsTable.name]
        val partnerName = row[PartnersTable.businessName]
        val endDate = row[PlatformSubscriptionsTable.endDate] // LocalDateTime
        val dateStr = DateTimeFormatter.ISO_LOCAL_DATE.format(endDate)

        val partnerId = row[PartnersTable.id].value
        val partnerOwnerId = row[PartnersTable.owner].value

        // 2. Уведомление ВЛАДЕЛЬЦА (Owner)
        val owner = UsersTable.select { UsersTable.id eq partnerOwnerId }.singleOrNull()

        if (owner != null) {
            val email = owner[UsersTable.email]
            val phone = owner[UsersTable.phoneNumber]
            val lang = owner[UsersTable.language] // "ru", "en", "ky"

            // А. СМС (Только если критично и есть телефон)
            if (isUrgent && phone.isNotBlank()) {
                // Берем чистый текст из ресурсов без HTML обертки
                val args = mapOf("pointName" to pointName, "date" to dateStr)
                val smsText = ServerResources.get(lang, "sub_critical_body", args)

                try {
                    smsService.sendSms(phone, smsText)
                    logNotification("SMS", "Owner", partnerId.toString())
                } catch (e: Exception) {
                    logger.error("Failed to send SMS to owner $phone", e)
                }
            }

            if (!email.isNullOrBlank()) {
                try {
                    val template = EmailTemplate.SubscriptionAlert(
                        isManager = false,
                        pointName = pointName,
                        partnerName = partnerName,
                        date = dateStr,
                        isUrgent = isUrgent
                    )

                    val subject = emailTemplateService.buildSubject(template, lang)
                    val body = emailTemplateService.buildBody(template, lang)

                    emailService.sendEmail(email, subject, body)
                    logNotification("EMAIL", "Owner", partnerId.toString())
                } catch (e: Exception) {
                    logger.error("Failed to send Email to owner $email", e)
                }
            }
        }

        val managers = PartnerStaffTable
            .innerJoin(UsersTable)
            .slice(UsersTable.email, UsersTable.language)
            .select {
                (PartnerStaffTable.partner eq partnerId) and
                        (PartnerStaffTable.role eq UserRole.PARTNER_MANAGER) and
                        (PartnerStaffTable.isActive eq true)
            }
            .map { it[UsersTable.email] to it[UsersTable.language] }

        managers.forEach { (mgrEmail, mgrLang) ->
            if (!mgrEmail.isNullOrBlank()) {
                try {
                    val template = EmailTemplate.SubscriptionAlert(
                        isManager = true, // Важно: текст для менеджера
                        pointName = pointName,
                        partnerName = partnerName,
                        date = dateStr,
                        isUrgent = isUrgent
                    )

                    val subject = emailTemplateService.buildSubject(template, mgrLang)
                    val body = emailTemplateService.buildBody(template, mgrLang)

                    emailService.sendEmail(mgrEmail, subject, body)
                    logNotification("EMAIL", "Manager", partnerId.toString())
                } catch (e: Exception) {
                    logger.error("Failed to send Email to manager $mgrEmail", e)
                }
            }
        }
    }

    private suspend fun notifyPlatformAdmins(
        warningSubs: List<ResultRow>,
        criticalSubs: List<ResultRow>
    ) = dbQuery {
        // 1. Находим получателей (Супер-Админы и Супер-Менеджеры)
        val admins = SystemStaffTable
            .innerJoin(UsersTable)
            .slice(UsersTable.email)
            .select {
                (SystemStaffTable.isActive eq true) and
                        (SystemStaffTable.role inList listOf(
                            UserRole.PLATFORM_SUPER_ADMIN,
                            UserRole.PLATFORM_SUPER_MANAGER
                        ))
            }
            .mapNotNull { it.toUserDto() }
            .filter { !it.email.isNullOrEmpty() }
            .distinct()

        if (admins.isEmpty()) return@dbQuery

        fun mapToItem(row: ResultRow): EmailTemplate.SuperAdminSummaryReport.SummaryItem {
            val requesterId = row[PlatformSubscriptionsTable.requester]

            val managerEmail = requesterId?.let { id ->
                UsersTable.slice(UsersTable.email)
                    .select { UsersTable.id eq id }
                    .singleOrNull()
                    ?.get(UsersTable.email)
            }

            return EmailTemplate.SuperAdminSummaryReport.SummaryItem(
                partner = row[PartnersTable.businessName],
                point = row[TradingPointsTable.name],
                date = DateTimeFormatter.ISO_LOCAL_DATE.format(row[PlatformSubscriptionsTable.endDate]),
                managerEmail = managerEmail ?: "N/A"
            )
        }

        val template = EmailTemplate.SuperAdminSummaryReport(
            critical = criticalSubs.map { mapToItem(it) },
            warning = warningSubs.map { mapToItem(it) }
        )


        // 4. Рассылка
        admins.forEach { admin ->
            val subject = emailTemplateService.buildSubject(template, admin.language)
            val body = emailTemplateService.buildBody(template, admin.language)
            try {
                emailService.sendEmail(admin.email!!, subject, body)
            } catch (e: Exception) {
                logger.error("Failed to send admin report to ${admin.email}", e)
            }
        }

        logNotification("EMAIL_SUMMARY", "Admins", "PLATFORM")
    }

    private suspend fun logNotification(channel: String, targetRole: String, partnerId: String) {
        systemEventRepository.logEvent(
            type = SystemEventType.NOTIFICATION_SENT,
            userId = "SYSTEM",
            partnerId = partnerId,
            payload = "Notification ($channel) sent to $targetRole regarding subscription."
        )
    }


}

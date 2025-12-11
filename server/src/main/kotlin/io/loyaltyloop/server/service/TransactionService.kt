package io.loyaltyloop.server.service

import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.TransactionRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.config.SecurityDefaults
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionResult
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.shared.models.TransactionSuccessType
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.CardRealtimeEventType
import io.loyaltyloop.shared.models.CardRealtimePayload
import io.loyaltyloop.shared.utils.CryptoUtils
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.shared.models.PartnerStatus
import kotlin.math.abs
import io.loyaltyloop.shared.models.CashierDailyStatsDto

class TransactionService(
    private val userRepository: UserRepository,
    private val transactionRepository: TransactionRepository,
    private val partnerRepository: PartnerRepository,
    private val realtimeService: CardRealtimeService,
    private val eventLogger: EventLogger
) {

    suspend fun getCashierDailyStats(cashierId: String): CashierDailyStatsDto {
        val zoneId = java.time.ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val todayStart = java.time.LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant().toEpochMilli()
        
        return transactionRepository.getCashierStats(cashierId, todayStart, now)
    }

    suspend fun scanQr(cashierUserId: String, request: ScanQrRequest): ScanQrResponse {
        // 1. Проверка прав кассира
        if (!partnerRepository.isUserCashierAtPoint(cashierUserId, request.tradingPointId)) {
            throw LoyaltyException(
                AppErrorCode.FORBIDDEN,
                "User is not a cashier at this trading point"
            )
        }


        val point = partnerRepository.getPointById(request.tradingPointId)

        if (!point.active) {
            throw LoyaltyException(AppErrorCode.POINT_INACTIVE, "Trading point is inactive")
        }
        if (point.temporarilyPaused) {
            throw LoyaltyException(AppErrorCode.POINT_PAUSED, "Trading point temporarily paused")
        }

        val partnerIdFromRepo = partnerRepository.getPartnerIdByPoint(request.tradingPointId)
        val partner = partnerRepository.getPartnerById(partnerIdFromRepo)

        if (partner.status == PartnerStatus.BLOCKED) {
            throw LoyaltyException(AppErrorCode.PARTNER_BLOCKED, "Partner account is blocked")
        }

        // 3. Парсинг QR-кода
        val qrData = parseQrCode(request.qrContent)

        // 4. Проверка времени (защита от replay атак)
        val now = System.currentTimeMillis() / 1000
        if (abs(now - qrData.timestamp) > SecurityDefaults.QR_TOKEN_TTL_SECONDS) {
            throw LoyaltyException(
                AppErrorCode.QR_EXPIRED,
                "QR code expired. Please refresh the screen."
            )
        }

        // 5. Получение и проверка клиента
        val customer = userRepository.getUserById(qrData.userId)
            ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND)

        // 6. Проверка подписи
        validateQrSignature(customer, qrData.timestamp, qrData.signature)

        // 7. Найти или создать карту (Сквозная лояльность: карта привязана к Партнеру)
        val (card, isCreatedNow) = userRepository.findOrCreateCard(
            userId = customer.id,
            partnerId = partner.id,
            partnerName = partner.businessName,
            partnerColor = partner.color,
            partnerLogo = partner.logoUrl,
            defaultVisitsTarget = partner.defaultVisitsTarget
        )

        if (card.legacyIsBlocked){
            throw LoyaltyException(AppErrorCode.CARD_BLOCKED)
        }

        if (!isCreatedNow) {
            ensureCardActive(card)
        }

        if (isCreatedNow) {
            realtimeService.notifyUser(
                userId = customer.id,
                payload = CardRealtimePayload(
                    eventType = CardRealtimeEventType.CARD_CREATED,
                    cardId = card.id,
                    cardSnapshot = card,
                    tradingPointId = request.tradingPointId
                )
            )
        }

        // 8. Получение настроек лояльности текущей точки
        val settings = partnerRepository.getSettingsByPointId(request.tradingPointId)

        // 9. Определение текущего процента кешбэка
        val currentTier = settings.tiers.find { it.levelIndex == card.tierLevel }
        val percent = currentTier?.cashbackPercent ?: 0.0

        return ScanQrResponse(
            userId = customer.id,
            userPhone = customer.phoneNumber,
            firstName = customer.firstName,
            cardId = card.id,
            currentBalance = card.balance,
            visitsCount = card.visitsCount,
            programType = settings.programType,
            visitsTarget = settings.visitsTarget,
            cashbackPercent = percent,
            maxBurnPercentage = settings.maxBurnPercentage,
            currency = point.currency,
            awardOnMixedPayment = settings.awardOnMixedPayment,
            isNewCard = isCreatedNow,
            trustScore = card.trustScore,
            riskLevel = card.riskLevel,
            fraudFlag = card.fraudFlag
        )
    }

    suspend fun calculateTransaction(
        cashierUserId: String,
        tradingPointId: String,
        cardId: String,
        purchaseAmount: Double,
        strategy: TransactionStrategy
    ): TransactionCalculationDto {

        if (!partnerRepository.isUserCashierAtPoint(cashierUserId, tradingPointId)) {
            throw LoyaltyException(
                AppErrorCode.FORBIDDEN,
                "User is not a cashier at this trading point"
            )
        }

        val point = partnerRepository.getPointById(tradingPointId)
        if (!point.active) {
            throw LoyaltyException(AppErrorCode.POINT_INACTIVE)
        }
        if (point.temporarilyPaused) {
            throw LoyaltyException(AppErrorCode.POINT_PAUSED)
        }

        val card = transactionRepository.getCardById(cardId)
            ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")

        ensureCardActive(card)

        val settings = partnerRepository.getSettingsByPointId(tradingPointId)

        return LoyaltyCalculator.calculate(
            card = card,
            purchaseAmount = purchaseAmount,
            maxBurnPercentage = settings.maxBurnPercentage,
            settingsVisitTarget = settings.visitsTarget,
            settingsTiers = settings.tiers,
            strategy = strategy,
            awardOnMixedPayment = settings.awardOnMixedPayment
        )
    }

    @Suppress("LongMethod", "ThrowsCount", "CyclomaticComplexMethod")
    suspend fun processTransaction(
        cashierUserId: String,
        tradingPointId: String,
        cardId: String,
        purchaseAmount: Double,
        strategy: TransactionStrategy
    ): TransactionResult {

        // 1. Проверка прав
        if (!partnerRepository.isUserCashierAtPoint(cashierUserId, tradingPointId)) {
            throw LoyaltyException(
                AppErrorCode.FORBIDDEN,
                "User is not a cashier at this trading point"
            )
        }

        val point = partnerRepository.getPointById(tradingPointId)

        if (!point.active) {
            throw LoyaltyException(AppErrorCode.POINT_INACTIVE)
        }
        if (point.temporarilyPaused) {
            throw LoyaltyException(AppErrorCode.POINT_PAUSED)
        }

        // 2. Получаем карту и проверяем валидность
        val card = transactionRepository.getCardById(cardId)
            ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")

        ensureCardActive(card)

        // 3. Получаем настройки ТОЧКИ (чтобы понять, какую стратегию применять)
        val settings = partnerRepository.getSettingsByPointId(tradingPointId)
        val partnerId = partnerRepository.getPartnerIdByPoint(tradingPointId)
        val partner = partnerRepository.getPartnerById(partnerId) // Fetch partner to check status

        if (partner.status == PartnerStatus.BLOCKED) {
            throw LoyaltyException(AppErrorCode.PARTNER_BLOCKED, "Partner account is blocked")
        }

        // 3. Получаем проверяем условие настройки лояльности точки и то что пришло к нам
        with(settings) {
            if (programType == LoyaltyProgramType.TIERED_LTV && strategy == TransactionStrategy.VISIT) {
                throw LoyaltyException(
                    AppErrorCode.INVALID_REQUEST,
                    "Loyalty settings ${programType} is not compatible with strategy VISIT"
                )
            }
            if (programType == LoyaltyProgramType.VISIT_COUNTER && strategy != TransactionStrategy.VISIT) {
                throw LoyaltyException(
                    AppErrorCode.INVALID_REQUEST,
                    "Loyalty settings ${programType} is not compatible with strategy MONEY"
                )
            }
        }

        val calc = LoyaltyCalculator.calculate(
            card = card,
            purchaseAmount = purchaseAmount,
            maxBurnPercentage = settings.maxBurnPercentage,
            settingsVisitTarget = settings.visitsTarget,
            settingsTiers = settings.tiers,
            strategy = strategy,
            awardOnMixedPayment = settings.awardOnMixedPayment
        )

        if (calc.message == TransactionCalculationDto.LoyaltyMessage.TIERED_ERROR_AMOUNT) {
            throw LoyaltyException(AppErrorCode.INVALID_AMOUNT, "Invalid Amount")
        }

        if (strategy == TransactionStrategy.VISIT) {
            transactionRepository.incrementVisits(card.id)
            transactionRepository.recordTransaction(
                userId = card.userId,
                pointId = tradingPointId,
                cashierId = cashierUserId,
                type = "VISIT",
                amount = 0.0,
                pointsDelta = 0.0,
                visitsDelta = 1
            )
            eventLogger.log(
                type = SystemEventType.VISIT,
                userId = card.userId,
                partnerId = partnerId,
                payload = "Visit recorded at point $tradingPointId by cashier $cashierUserId. Visits delta: 1"
            )
        } else {
            // MONEY (CHARGE or SPEND)
            if (calc.pointsSpent > 0) {
                transactionRepository.addCashback(card.id, -calc.pointsSpent, 0.0)
                transactionRepository.recordTransaction(
                    userId = card.userId,
                    pointId = tradingPointId,
                    cashierId = cashierUserId,
                    type = "SPEND",
                    amount = 0.0,
                    pointsDelta = -calc.pointsSpent,
                    visitsDelta = 0
                )
                eventLogger.log(
                    type = SystemEventType.REDEMPTION,
                    userId = card.userId,
                    partnerId = partnerId,
                    payload = "Redeemed ${calc.pointsSpent} points at point $tradingPointId"
                )
            }

            if (calc.moneyPaid > 0 || calc.pointsToAward > 0) {
                transactionRepository.addCashback(card.id, calc.pointsToAward, calc.moneyPaid)
                transactionRepository.recordTransaction(
                    userId = card.userId,
                    pointId = tradingPointId,
                    cashierId = cashierUserId,
                    type = "EARN",
                    amount = calc.moneyPaid,
                    pointsDelta = calc.pointsToAward,
                    visitsDelta = 0
                )
                eventLogger.log(
                    type = SystemEventType.ACCRUAL,
                    userId = card.userId,
                    partnerId = partnerId,
                    payload = "Accrued ${calc.pointsToAward} points for ${calc.moneyPaid} amount at point $tradingPointId"
                )
            }

            val newTotalSpent = card.totalSpent + calc.moneyPaid
            val nextTier = settings.tiers
                .filter { it.threshold <= newTotalSpent }
                .maxByOrNull { it.levelIndex }

            if (nextTier != null && nextTier.levelIndex > card.tierLevel) {
                transactionRepository.updateTier(card.id, nextTier.levelIndex)
                eventLogger.log(
                    type = SystemEventType.TIER_CHANGE,
                    userId = card.userId,
                    partnerId = partnerId,
                    payload = "Tier upgraded to ${nextTier.levelIndex}"
                )
            }
        }

        // Определяем тип успешного результата для клиента
        val successType: TransactionSuccessType
        val successArgs: List<String>

        val visitIncrement = if (strategy == TransactionStrategy.VISIT) {
            (calc.newVisits - card.visitsCount).coerceAtLeast(1)
        } else {
            0
        }

        if (strategy == TransactionStrategy.VISIT) {
            val target = settings.visitsTarget
            if (target > 0 && calc.newVisits % target == 0 && calc.newVisits > 0) {
                successType = TransactionSuccessType.VISIT_REWARD
                successArgs = listOf(target.toString())
            } else {
                successType = TransactionSuccessType.VISIT_PROGRESS
                successArgs = listOf(
                    calc.newVisits.toString(),
                    target.toString(),
                    visitIncrement.toString()
                )
            }
        } else {
            // MONEY
            val spent = calc.pointsSpent
            val earned = calc.pointsToAward

            if (spent > 0 && earned > 0) {
                successType = TransactionSuccessType.POINTS_SPENT_EARNED
                successArgs = listOf(fmt(spent), fmt(earned))
            } else if (spent > 0) {
                successType = TransactionSuccessType.POINTS_SPENT
                successArgs = listOf(fmt(spent))
            } else if (earned > 0) {
                successType = TransactionSuccessType.POINTS_EARNED
                successArgs = listOf(fmt(earned))
            } else {
                successType = TransactionSuccessType.BALANCE_INFO
                successArgs = listOf(fmt(calc.newBalance))
            }
        }

        val result = TransactionResult(
            cardId = card.id,
            newBalance = calc.newBalance,
            newVisits = calc.newVisits,
            type = successType,
            args = successArgs
        )
        realtimeService.notifyUser(
            userId = card.userId,
            payload = CardRealtimePayload(
                eventType = CardRealtimeEventType.TRANSACTION,
                cardId = card.id,
                successType = successType,
                args = successArgs,
                newBalance = calc.newBalance,
                newVisits = calc.newVisits,
                tradingPointId = tradingPointId
            )
        )

        return result
    }

    private fun fmt(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    /**
     * Получает историю транзакций для Партнера
     */
    suspend fun getPartnerHistory(ownerId: String): List<io.loyaltyloop.shared.models.TransactionHistoryDto> {
        val partner = partnerRepository.getPartnerByUserId(ownerId)

        return transactionRepository.getHistoryForPartner(partner.id)
    }

    // --- Analytics ---

    suspend fun getAnalytics(
        ownerId: String,
        period: io.loyaltyloop.shared.models.AnalyticsPeriod
    ): io.loyaltyloop.shared.models.AnalyticsResponse {
        val partner = partnerRepository.getPartnerByUserId(ownerId)

        val now = System.currentTimeMillis()
        val (from, grouping) = when (period) {
            io.loyaltyloop.shared.models.AnalyticsPeriod.WEEK -> (now - 6 * 24 * 3600 * 1000L) to GroupingType.DAY // 7 days including today
            io.loyaltyloop.shared.models.AnalyticsPeriod.MONTH -> (now - 29 * 24 * 3600 * 1000L) to GroupingType.DAY // 30 days
            io.loyaltyloop.shared.models.AnalyticsPeriod.SIX_MONTHS -> (now - 180L * 24 * 3600 * 1000L) to GroupingType.MONTH
            io.loyaltyloop.shared.models.AnalyticsPeriod.YEAR -> (now - 365L * 24 * 3600 * 1000L) to GroupingType.MONTH
        }

        val transactions = transactionRepository.getTransactionsForAnalytics(partner.id, from, now)

        // 1. Group existing data
        val groupedMap = transactions.groupBy {
            formatDate(it.timestamp, grouping)
        }

        // 2. Generate all dates in range (to fill gaps with 0)
        val allLabels = generateDateLabels(from, now, grouping)

        // 3. Map all labels to points
        val points = allLabels.map { dateLabel ->
            val txList = groupedMap[dateLabel] ?: emptyList()
            io.loyaltyloop.shared.models.RevenueChartPoint(
                date = dateLabel,
                revenue = txList.sumOf { it.amount },
                transactionsCount = txList.size
            )
        }

        val totalRevenue = transactions.sumOf { it.amount }
        val totalTransactions = transactions.size
        val averageCheck = if (totalTransactions > 0) totalRevenue / totalTransactions else 0.0

        return io.loyaltyloop.shared.models.AnalyticsResponse(
            totalRevenue = totalRevenue,
            totalTransactions = totalTransactions,
            averageCheck = averageCheck,
            chartData = points
        )
    }

    private enum class GroupingType { DAY, MONTH }

    private fun formatDate(timestamp: Long, type: GroupingType): String {
        val instant = java.time.Instant.ofEpochMilli(timestamp)
        val zone = java.time.ZoneId.systemDefault()
        val ldt = java.time.LocalDateTime.ofInstant(instant, zone)

        return when (type) {
            GroupingType.DAY -> ldt.toLocalDate().toString()
            GroupingType.MONTH -> "${ldt.year}-${ldt.monthValue.toString().padStart(2, '0')}"
        }
    }

    private fun generateDateLabels(from: Long, to: Long, type: GroupingType): List<String> {
        val zone = java.time.ZoneId.systemDefault()
        val startLdt = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zone)
        val endLdt = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(to), zone)

        val labels = mutableSetOf<String>()
        var current = startLdt

        // Limit loop to prevent infinite cycle in case of error (max 400 iterations covers year)
        var steps = 0
        while (!current.isAfter(endLdt) && steps < 400) {
            val label = when (type) {
                GroupingType.DAY -> current.toLocalDate().toString()
                GroupingType.MONTH -> "${current.year}-${
                    current.monthValue.toString().padStart(2, '0')
                }"
            }
            labels.add(label)

            current = when (type) {
                GroupingType.DAY -> current.plusDays(1)
                GroupingType.MONTH -> current.plusMonths(1)
            }
            steps++
        }

        // Ensure 'today' is included if loop missed it due to time calculation
        val lastLabel = when (type) {
            GroupingType.DAY -> endLdt.toLocalDate().toString()
            GroupingType.MONTH -> "${endLdt.year}-${endLdt.monthValue.toString().padStart(2, '0')}"
        }
        labels.add(lastLabel)

        return labels.sorted()
    }

    // --- Helpers ---

    private data class QrData(val userId: String, val timestamp: Long, val signature: String)

    private fun parseQrCode(qrContent: String): QrData {
        val parts = qrContent.split(":")
        if (parts.size != 4 || parts[0] != "loyalty_v1") {
            throw LoyaltyException(AppErrorCode.INVALID_QR_SIGNATURE, "Invalid QR code format")
        }
        return QrData(
            userId = parts[1],
            timestamp = parts[2].toLongOrNull()
                ?: throw LoyaltyException(AppErrorCode.INVALID_QR_SIGNATURE, "Invalid timestamp"),
            signature = parts[3]
        )
    }

    private fun validateQrSignature(customer: UserDto, timestamp: Long, clientSignature: String) {
        if (customer.qrSecret.isBlank()) throw LoyaltyException(AppErrorCode.SECURITY_QR_SECRET_MISSING)
        val data = "${customer.id}:$timestamp"
        val expected = CryptoUtils.hmacSha256(customer.qrSecret, data)
        if (expected != clientSignature) throw LoyaltyException(AppErrorCode.INVALID_QR_SIGNATURE)
    }

    private fun ensureCardActive(card: LoyaltyCardDto) {
        val now = System.currentTimeMillis()
        card.block?.takeIf { it.until > now }?.let { block ->
            throw LoyaltyException(AppErrorCode.CARD_BLOCKED, block.reason ?: "Card is blocked")
        }
        card.pause?.let { pause ->
            throw LoyaltyException(AppErrorCode.CARD_PAUSED, pause.reason ?: "Card is paused")
        }
    }
}

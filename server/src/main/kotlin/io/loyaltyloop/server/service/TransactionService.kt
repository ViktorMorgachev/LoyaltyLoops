package io.loyaltyloop.server.service

import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.HistoryRepository
import io.loyaltyloop.server.repository.LoyaltyCardRepository
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.PartnerStaffRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.repository.TransactionRecord
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.shared.config.SecurityDefaults
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CardRealtimeEventType
import io.loyaltyloop.shared.models.CardRealtimePayload
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltySettingsDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.ScanQrRequest
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionHistoryDto
import io.loyaltyloop.shared.models.TransactionResult
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.shared.models.TransactionSuccessType
import io.loyaltyloop.shared.models.TransactionTypeHistory
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.utils.CryptoUtils
import io.loyaltyloop.shared.utils.LoyaltyFormatter
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime
import kotlin.math.abs

// TODO Checked
private const val QR_PARTS_COUNT = 4

class TransactionService(
    private val userRepository: UserRepository,
    private val partnerRepository: PartnerRepository,
    private val partnerStaffRepository: PartnerStaffRepository,
    private val tradingPointRepository: TradingPointRepository,
    private val realtimeService: CardRealtimeService,
    private val eventLogger: EventLogger,
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val exchangeRateService: ExchangeRateService,
    private val historyRepository: HistoryRepository
) {

    private val logger = org.slf4j.LoggerFactory.getLogger(TransactionService::class.java)
    suspend fun scanQr(
        cashierUserId: String,
        tradingPointId: String,
        estimatedCurrency: String,
        request: ScanQrRequest
    ): ScanQrResponse {
        // 1. Проверка прав кассира
        if (!partnerStaffRepository.isUserCashierAtPoint(cashierUserId, tradingPointId)) {
            throw LoyaltyException(AppErrorCode.FORBIDDEN, "User is not a cashier at this trading point")
        }

        // 2. Валидация точки и статуса партнера
        val point = tradingPointRepository.getPointById(tradingPointId)
        if (!point.active) throw LoyaltyException(AppErrorCode.POINT_INACTIVE)
        if (point.temporarilyPaused) throw LoyaltyException(AppErrorCode.POINT_PAUSED)

        val partnerId = tradingPointRepository.getPartnerIdByPointId(tradingPointId)
        val partner = partnerRepository.getPartnerByIdOrThrow(partnerId, loadOtherData = false)

        if (partner.status == PartnerStatus.BLOCKED) {
            throw LoyaltyException(AppErrorCode.PARTNER_BLOCKED)
        }

        // 3. Обработка QR-кода
        val qrData = parseQrCode(request.qrContent)
        validateQrTimestamp(qrData.timestamp)

        val customer = userRepository.getUserById(qrData.userId)
            ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND)

        validateQrSignature(customer, qrData.timestamp, qrData.signature)

        // 4. Работа с картой лояльности
        val (card, isCreatedNow) = loyaltyCardRepository.findOrCreateCard(
            userId = customer.id,
            partnerId = partnerId,
            estimatedCurrency = estimatedCurrency
        )

        ensureCardActive(card)

        if (isCreatedNow) {
            logger.info("Card created for user=${customer.id}, cardId=${card.id}, partner=$partnerId, point=$tradingPointId")
            notifyUserCardCreated(customer.id, card, tradingPointId)
        }

        // 5. Формирование ответа
        val settings = tradingPointRepository.getSettingsByPointId(tradingPointId)
        val currentTier = settings.tiers.find { it.levelIndex == card.tierLevel }
        val cashbackPercent = currentTier?.cashbackPercent ?: 0.0

        return ScanQrResponse(
            userId = customer.id,
            userPhone = customer.phoneNumber,
            firstName = customer.firstName,
            cardId = card.id,
            currentBalance = card.estimatedValue,
            visitsCount = card.visitsCount,
            programType = settings.programType,
            visitsTarget = settings.visitsTarget,
            cashbackPercent = cashbackPercent,
            maxBurnPercentage = settings.maxBurnPercentage,
            currency = point.currency,
            awardOnMixedPayment = settings.awardOnMixedPayment,
            isNewCard = isCreatedNow,
            trustScore = card.trustScore,
            riskLevel = card.riskLevel,
            fraudFlag = card.fraudFlag
        )
    }

    private fun validateQrTimestamp(timestamp: Long) {
        val now = System.currentTimeMillis() / 1000
        if (abs(now - timestamp) > SecurityDefaults.QR_TOKEN_TTL_SECONDS) {
            throw LoyaltyException(AppErrorCode.QR_EXPIRED, "QR code expired. Please refresh the screen.")
        }
    }

    private suspend fun notifyUserCardCreated(userId: String, card: LoyaltyCardDto, tradingPointId: String) {
        realtimeService.notifyUser(
            userId = userId,
            payload = CardRealtimePayload(
                eventType = CardRealtimeEventType.CARD_CREATED,
                cardId = card.id,
                cardSnapshot = card,
                tradingPointId = tradingPointId
            )
        )
    }

    /**
     * Рассчитывает параметры транзакции (предпросмотр) БЕЗ её выполнения.
     * Используется для отображения кассиру информации о том, сколько баллов будет списано/начислено.
     */
    suspend fun calculateTransaction(
        cashierUserId: String,
        tradingPointId: String,
        cardId: String,
        purchaseAmount: Double,
        strategy: TransactionStrategy,
        estimatedCurrency: String,
    ): TransactionCalculationDto {

        validateTransactionAccess(cashierUserId, tradingPointId)

        val card = loyaltyCardRepository.getCardByID(cardId = cardId, estimatedCurrency = estimatedCurrency)
            ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")

        ensureCardActive(card)

        val point = tradingPointRepository.getPointById(tradingPointId)
        val partner = partnerRepository.getPartnerByPointId(tradingPointId)
            ?: throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND, "Partner not found")

        // 1. Получаем курс валют: Base (USD) -> Local (KGS). Пример: 85.0
        // Если валюты совпадают, курс = 1.0
        val settings = tradingPointRepository.getSettingsByPointId(tradingPointId)
        val rate = exchangeRateService.getRate(partner.baseCurrency, point.currency)

        // 2. Создаем "Виртуальную карту" в местной валюте для корректных расчетов
        // Если у клиента 10 USD (в базе), калькулятор должен оперировать 850 KGS
        val localCard = card.copy(
            balance = LoyaltyFormatter.round(card.balance * rate),
            totalSpent = LoyaltyFormatter.round(card.totalSpent * rate) // Важно для проверки Tier Update в местной валюте
        )

        // Также конвертируем пороги уровней (Tiers) в локальную валюту
        val localTiers = settings.tiers.map { tier ->
            tier.copy(threshold = LoyaltyFormatter.round(tier.threshold * rate))
        }

        // 3. Выполняем "чистый" расчет через Calculator
        val calc = LoyaltyCalculator.calculate(
            settingsTiers = localTiers,
            card = localCard,
            purchaseAmount = purchaseAmount,
            maxBurnPercentage = settings.maxBurnPercentage,
            settingsVisitTarget = settings.visitsTarget,
            strategy = strategy,
            awardOnMixedPayment = settings.awardOnMixedPayment
        )

        // Возвращаем результат с указанием валюты точки и курса обмена
        return calc.copy(
            currency = point.currency,
            exchangeRate = rate
        )
    }

    /**
     * Выполняет транзакцию:
     * 1. Валидация
     * 2. Расчет (аналогично calculateTransaction)
     * 3. Обновление БД (списание/начисление, история)
     * 4. Отправка уведомлений (Realtime)
     */
    suspend fun processTransaction(
        cashierUserId: String,
        tradingPointId: String,
        cardId: String,
        purchaseAmount: Double,
        strategy: TransactionStrategy,
        estimatedCurrency: String,
    ) = newSuspendedTransaction(Dispatchers.IO) {

        validateTransactionAccess(cashierUserId, tradingPointId)

        val staffId = partnerStaffRepository.getCassierById(cashierUserId, tradingPointId)?.toString()
            ?: throw LoyaltyException(AppErrorCode.FORBIDDEN, "Cashier staff record not found")

        // Сериализуем параллельные операции по карте: баланс ниже читается уже под блокировкой
        loyaltyCardRepository.lockCardRow(cardId)

        val card = loyaltyCardRepository.getCardByID(cardId = cardId, estimatedCurrency = estimatedCurrency)
            ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Card not found")

        ensureCardActive(card)

        val point = tradingPointRepository.getPointById(tradingPointId)
        val partner = partnerRepository.getPartnerByPointId(tradingPointId)
            ?: throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND, "Partner not found")

        // 1. Получаем курс: Base (USD) -> Local (KGS). Пример: 85.0
        val settings = tradingPointRepository.getSettingsByPointId(tradingPointId)
        val rate = exchangeRateService.getRate(partner.baseCurrency, point.currency)

        validateLoyaltyStrategyCompatibility(settings.programType, strategy)

        // 4. Расчет лояльности (в локальной валюте)
        val calc = calculateLoyalty(card, purchaseAmount, strategy, settings, rate)
        if (calc.message == TransactionCalculationDto.LoyaltyMessage.TIERED_ERROR_AMOUNT) {
            throw LoyaltyException(AppErrorCode.INVALID_AMOUNT, "Invalid Amount")
        }

        val updatedAt = nowUtc()

        // 5. Выполнение транзакции в зависимости от стратегии (Money vs Visit)
        val cashierResult: TransactionResult
        val clientPayload: CardRealtimePayload

        if (strategy == TransactionStrategy.VISIT) {
            // Обработка визитов (штампиков)
            val (cRes, cPayload) = processVisitTransaction(
                card, staffId, tradingPointId, point.currency, rate, settings, calc, updatedAt
            )
            cashierResult = cRes
            clientPayload = cPayload
        } else {
            // Обработка денежных транзакций (Баллы/Кэшбэк)
            val (cRes, cPayload) = processMoneyTransaction(
                card,  staffId, tradingPointId, point.currency, rate, calc, updatedAt
            )
            cashierResult = cRes
            clientPayload = cPayload

            // Проверка и обновление уровня лояльности (Tier) если сумма трат достигла порога
            val tierUpgraded = updateTierIfEligible(card.id, estimatedCurrency, settings.tiers, updatedAt)
            if (tierUpgraded) {
                eventLogger.log(
                    type = SystemEventType.TIER_UPGRADED,
                    userId = card.userId,
                    partnerId = card.partnerId,
                    payload = "Tier was upgraded for card: ${card.id}"
                )
                // Если уровень повысился, клиент может захотеть отдельное событие (например, для анимации).
                // Но так как ниже мы отправляем notifyUserTransaction с полным snapshot-ом карты (где уже новый уровень),
                // клиент (мобилка) сама увидит изменение уровня и покажет анимацию TierUpgrade.
                // Поэтому явно отправлять еще одно событие не обязательно, но флаг tierUpgraded может пригодиться для логов или статистики.
            }
        }

        // 6. Уведомление
        // Важно: Получаем свежее состояние карты с учетом всех изменений (в том числе estimatedValue и tierLevel)
        // Чтобы клиент получил актуальные данные сразу (без рефреша)
        val finalCardSnapshot = loyaltyCardRepository.getCardByID(cardId, estimatedCurrency)

        val finalPayload = clientPayload.copy(cardSnapshot = finalCardSnapshot)

        notifyUserTransaction(card.userId, finalPayload)

        cashierResult
    }

    private suspend fun validateTransactionAccess(cashierUserId: String, tradingPointId: String) {
        if (!partnerStaffRepository.isUserCashierAtPoint(cashierUserId, tradingPointId)) {
            throw LoyaltyException(AppErrorCode.FORBIDDEN, "User is not a cashier at this trading point")
        }
        val point = tradingPointRepository.getPointById(tradingPointId)
        if (!point.active) throw LoyaltyException(AppErrorCode.POINT_INACTIVE)
        if (point.temporarilyPaused) throw LoyaltyException(AppErrorCode.POINT_PAUSED)
    }

    private fun validateLoyaltyStrategyCompatibility(programType: LoyaltyProgramType, strategy: TransactionStrategy) {
        if (programType == LoyaltyProgramType.TIERED_LTV && strategy == TransactionStrategy.VISIT) {
            throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Loyalty settings $programType is not compatible with strategy VISIT")
        }
        if (programType == LoyaltyProgramType.VISIT_COUNTER && strategy != TransactionStrategy.VISIT) {
            throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Loyalty settings $programType is not compatible with strategy MONEY")
        }
    }

    private fun calculateLoyalty(
        card: LoyaltyCardDto,
        purchaseAmount: Double,
        strategy: TransactionStrategy,
        settings: LoyaltySettingsDto,
        rate: Double
    ): TransactionCalculationDto {
        // Конвертация в локальную валюту для калькулятора
        val localCard = card.copy(
            balance = LoyaltyFormatter.round(card.balance * rate),
            totalSpent = LoyaltyFormatter.round(card.totalSpent * rate)
        )
        val localTiers = settings.tiers.map { tier ->
            tier.copy(threshold = LoyaltyFormatter.round(tier.threshold * rate))
        }

        return LoyaltyCalculator.calculate(
            card = localCard,
            purchaseAmount = purchaseAmount,
            maxBurnPercentage = settings.maxBurnPercentage,
            settingsVisitTarget = settings.visitsTarget,
            settingsTiers = localTiers,
            strategy = strategy,
            awardOnMixedPayment = settings.awardOnMixedPayment
        )
    }

    private suspend fun processVisitTransaction(
        card: LoyaltyCardDto,
        staffId: String,
        tradingPointId: String,
        currency: String,
        rate: Double,
        settings: LoyaltySettingsDto,
        calc: TransactionCalculationDto,
        updatedAt: LocalDateTime
    ): Pair<TransactionResult, CardRealtimePayload> {

        val target = settings.visitsTarget
        val isReward = target > 0 && calc.newVisits % target == 0 && calc.newVisits > 0

        val typeStr = if (isReward) "VISIT_REWARD" else "VISIT_PROGRESS"
        val successType = if (isReward) TransactionSuccessType.VISIT_REWARD else TransactionSuccessType.VISIT_PROGRESS

        val successArgs = if (isReward) {
            loyaltyCardRepository.dropVisits(card.id, updatedAt)
            listOf(target.toString())
        } else {
            loyaltyCardRepository.incrementVisits(card.id, updatedAt)
            listOf(
                calc.newVisits.toString(),
                target.toString(),
                (calc.newVisits - card.visitsCount).coerceAtLeast(1).toString()
            )
        }

        historyRepository.recordTransaction(
            TransactionRecord(
                userId = card.userId,
                pointId = tradingPointId,
                cashierId = staffId,
                type = typeStr,
                amount = 0.0,
                pointsDelta = 0.0,
                visitsDelta = 1,
                currency = currency,
                exchangeRate = rate,
                updatedAt = updatedAt,
                pointsBaseValue = 0.0
            )
        )

        eventLogger.log(
            type = SystemEventType.VISIT,
            userId = card.userId,
            partnerId = card.partnerId,
            payload = "Visit recorded at point $tradingPointId by cashier $staffId"
        )

        val result = TransactionResult(
            cardId = card.id,
            newBalance = calc.newBalance,
            newVisits = calc.newVisits,
            type = successType,
            currency = currency,
            args = successArgs
        )

        val payload = CardRealtimePayload(
            eventType = CardRealtimeEventType.TRANSACTION,
            cardId = card.id,
            successType = successType,
            args = successArgs,
            newBalance = calc.newBalance, // Visits logic is simpler, balance not affected usually
            newVisits = calc.newVisits,
            tradingPointId = tradingPointId
        )

        return result to payload
    }

    private suspend fun processMoneyTransaction(
        card: LoyaltyCardDto,
        staffId: String,
        tradingPointId: String,
        currency: String,
        rate: Double,
        calc: TransactionCalculationDto,
        updatedAt: LocalDateTime
    ): Pair<TransactionResult, CardRealtimePayload> {
        // Возвращаем ДВА разных объекта результата
        fun toBase(localVal: Double): Double = LoyaltyFormatter.round(localVal / rate)
        val moneyPaidBase = toBase(calc.moneyPaid)

        // Логика "Слияния": Если списание есть, деньги есть, но начисления НЕТ (awardOnMixedPayment=false)
        val mergeMoneyIntoSpend = calc.pointsSpent > 0 && calc.moneyPaid > 0 && calc.pointsToAward == 0.0

        // Определения для понимания валют:
        // Local - валюта ТОРГОВОЙ ТОЧКИ (например, KGS - сомы).
        // Base - валюта АККАУНТА ПАРТНЕРА/СЕТИ (например, USD - доллары или условные "Баллы").
        // Кассир оперирует локальной валютой (чек в сомах).
        // Система лояльности хранит баланс пользователя в базовой валюте (чтобы работало во всех точках сети).

        val spentLocal = calc.pointsSpent // Сколько списали скидки в валюте точки (KGS)
        val earnedLocal = calc.pointsToAward // Сколько начислили кэшбэка в пересчете на валюту точки (KGS)
        val earnedBase = toBase(earnedLocal) // Сколько это будет в "Баллах" (USD), которые упадут на счет клиента

        var cashierSuccessType: TransactionSuccessType
        var cashierArgs: List<String>

        var clientSuccessType: TransactionSuccessType
        var clientArgs: List<String>

        // 1. Формирование ответа для КАССИРА (Все в ЛОКАЛЬНОЙ валюте)
        // Пример для кассира:
        // 1. Списание + Начисление -> "Списано: 500, Начислено: 150" (В валюте точки, например, сомах)
        // 2. Только списание -> "Списано: 500"
        // 3. Только начисление -> "Начислено: 150"
        // 4. Инфо -> "Баланс: 1200" (Актуальный баланс в валюте точки)
        if (spentLocal > 0 && earnedLocal > 0) {
            cashierSuccessType = TransactionSuccessType.POINTS_SPENT_EARNED
            cashierArgs = listOf(fmt(spentLocal), fmt(earnedLocal))
        } else if (spentLocal > 0) {
            cashierSuccessType = TransactionSuccessType.POINTS_SPENT
            cashierArgs = listOf(fmt(spentLocal))
        } else if (earnedLocal > 0) {
            cashierSuccessType = TransactionSuccessType.POINTS_EARNED
            cashierArgs = listOf(fmt(earnedLocal))
        } else {
            cashierSuccessType = TransactionSuccessType.BALANCE_INFO
            cashierArgs = listOf(fmt(calc.newBalance))
        }

        // 2. Формирование ответа для КЛИЕНТА (Обычно в BASE валюте, но если currencies differ -> показываем approx)
        // Для простоты покажем Базовые баллы. Если есть разница курсов, можно добавить второй аргумент.
        // Но TransactionSuccessType имеет фиксированное кол-во аргументов для UI.
        // Если мы хотим показать "Начислено 10 баллов (~850 сом)", нам нужно либо менять successType,
        // либо передавать "10 (~850 сом)" как один аргумент.

        val showApprox = rate != 1.0 && rate > 0

        fun formatEarned(base: Double, local: Double): String {
            return if (showApprox) "${fmt(base)} (~${fmt(local)} $currency)" else fmt(base)
        }

        // Пример для клиента (push/экран):
        // 1. Списание + Начисление -> "Списано: 500 (скидка в местной валюте), Начислено: 10 (~850 сом)" (Баллы в базовой валюте)
        // 2. Только списание -> "Списано: 500" (Скидка всегда локальна)
        // 3. Только начисление -> "Начислено: 10 (~850 сом)" (Баллы клиента растут в базовой валюте)
        // 4. Инфо -> "Баланс: 150" (Общий баланс клиента в базовой валюте)

        // Исправление: Клиенту показываем списание в БАЗОВОЙ валюте (баллах), так как это списание с его баланса.
        val spentBase = toBase(spentLocal)

        if (spentLocal > 0 && earnedBase > 0) {
            clientSuccessType = TransactionSuccessType.POINTS_SPENT_EARNED
            // Списание показываем в баллах (spentBase), Начисление - в баллах (earnedBase)
            clientArgs = listOf(fmt(spentBase), formatEarned(earnedBase, earnedLocal))
        } else if (spentLocal > 0) {
            clientSuccessType = TransactionSuccessType.POINTS_SPENT
            clientArgs = listOf(fmt(spentBase))
        } else if (earnedBase > 0) {
            clientSuccessType = TransactionSuccessType.POINTS_EARNED
            clientArgs = listOf(formatEarned(earnedBase, earnedLocal))
        } else {
            clientSuccessType = TransactionSuccessType.BALANCE_INFO
            clientArgs = listOf(fmt(toBase(calc.newBalance)))
        }

        if (spentLocal > 0) {
            // val spentBase = toBase(spentLocal) // Уже вычислено выше

             val moneyToRecordInSpend = if (mergeMoneyIntoSpend) calc.moneyPaid else 0.0
             val moneyAddToLtv = if (mergeMoneyIntoSpend) moneyPaidBase else 0.0

            loyaltyCardRepository.addCashback(card.id, -spentBase, moneyAddToLtv, updatedAt)

            historyRepository.recordTransaction(
                TransactionRecord(
                    userId = card.userId,
                    pointId = tradingPointId,
                    cashierId = staffId,
                    type = TransactionTypeHistory.POINTS_SPENT.name,
                    amount = moneyToRecordInSpend,
                    pointsDelta = -spentLocal,
                    visitsDelta = 0,
                    currency = currency,
                    exchangeRate = rate,
                    pointsBaseValue = -spentBase,
                    updatedAt = updatedAt
                )
            )
             eventLogger.log(
                type = SystemEventType.REDEMPTION,
                userId = card.userId,
                partnerId = card.partnerId,
                payload = "Redeemed $spentLocal points at point $tradingPointId"
            )
        }

        // 2. Обработка НАЧИСЛЕНИЯ (Earn)
        if (!mergeMoneyIntoSpend && (calc.moneyPaid > 0 || calc.pointsToAward > 0)) {

            val earnedBaseVal = toBase(earnedLocal)
            val moneyAddToLtv = moneyPaidBase

            loyaltyCardRepository.addCashback(card.id, earnedBaseVal, moneyAddToLtv, updatedAt)

            historyRepository.recordTransaction(
                TransactionRecord(
                    userId = card.userId,
                    pointId = tradingPointId,
                    cashierId = staffId,
                    type = TransactionTypeHistory.EARN.name,
                    amount = calc.moneyPaid,
                    pointsDelta = earnedLocal,
                    visitsDelta = 0,
                    currency = currency,
                    exchangeRate = rate,
                    pointsBaseValue = earnedBaseVal,
                    updatedAt = updatedAt
                )
            )
             eventLogger.log(
                type = SystemEventType.ACCRUAL,
                userId = card.userId,
                partnerId = card.partnerId,
                payload = "Accrued $earnedLocal points at point $tradingPointId"
            )
        }

        val cashierResult = TransactionResult(
            cardId = card.id,
            newBalance = calc.newBalance, // Local balance for Cashier
            newVisits = calc.newVisits,
            type = cashierSuccessType,
            currency = currency,
            args = cashierArgs
        )

        val clientPayload = CardRealtimePayload(
            eventType = CardRealtimeEventType.TRANSACTION,
            cardId = card.id,
            successType = clientSuccessType,
            args = clientArgs,
            newBalance = toBase(calc.newBalance), // Base balance for Client
            newVisits = calc.newVisits,
            tradingPointId = tradingPointId
        )

        return cashierResult to clientPayload
    }

    private suspend fun updateTierIfEligible(
        cardId: String,
        estimatedCurrency: String,
        tiers: List<LoyaltyTierDto>,
        updatedAt: LocalDateTime
    ): Boolean {
        val refreshedCard = loyaltyCardRepository.getCardByID(cardId, estimatedCurrency) ?: return false
        val nextTier = tiers
            .filter { tier -> tier.threshold <= refreshedCard.totalSpent }
            .maxByOrNull { it.levelIndex }

        if (nextTier != null && nextTier.levelIndex > refreshedCard.tierLevel) {
            loyaltyCardRepository.updateTier(cardId, nextTier.levelIndex, updatedAt)
            eventLogger.log(
                type = SystemEventType.TIER_CHANGE,
                userId = refreshedCard.userId,
                partnerId = refreshedCard.partnerId,
                payload = "Tier upgraded to ${nextTier.levelIndex}"
            )
            return true
        }
        return false
    }

    private suspend fun notifyUserTransaction(
        userId: String,
        payload: CardRealtimePayload
    ) {
        realtimeService.notifyUser(
            userId = userId,
            payload = payload
        )
    }

    private fun fmt(value: Double): String {
        return LoyaltyFormatter.format(value)
    }

    /**
     * Получает историю транзакций для Партнера
     */
    suspend fun getPartnerHistory(partnerId: String): List<TransactionHistoryDto> {
        return historyRepository.getHistoryForPartner(partnerId)
    }

    // --- Helpers ---

    private data class QrData(val userId: String, val timestamp: Long, val signature: String)

    private fun parseQrCode(qrContent: String): QrData {
        val parts = qrContent.split(":")
        if (parts.size != QR_PARTS_COUNT || parts[0] != "loyalty_v1") {
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

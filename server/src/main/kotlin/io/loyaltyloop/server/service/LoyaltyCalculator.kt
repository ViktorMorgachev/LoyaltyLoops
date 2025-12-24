package io.loyaltyloop.server.service

import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.shared.utils.LoyaltyFormatter

// TODO checked
object LoyaltyCalculator {

    /**
     * Основной метод расчета лояльности.
     * Не изменяет состояние базы данных, только возвращает DTO с результатами расчета.
     *
     * @param card карта лояльности пользователя (состояние ДО транзакции)
     * @param purchaseAmount сумма покупки в локальной валюте (например, KGS)
     * @param maxBurnPercentage макс. % от суммы чека, который можно оплатить баллами (напр. 30%)
     * @param settingsVisitTarget цель визитов для начисления награды (для стратегии VISIT)
     * @param settingsTiers список уровней лояльности (должен быть конвертирован в локальную валюту)
     * @param strategy тип операции: VISIT (визиты), CHARGE (только начисление), SPEND (списание)
     * @param awardOnMixedPayment начислять ли кэшбэк на остаток суммы при списании
     */
    fun calculate(
        card: LoyaltyCardDto,
        purchaseAmount: Double, // В локальной валюте (KGS)
        maxBurnPercentage: Int,
        settingsVisitTarget: Int,
        settingsTiers: List<LoyaltyTierDto>, // В локальной валюте (KGS)
        strategy: TransactionStrategy,
        awardOnMixedPayment: Boolean = false
    ): TransactionCalculationDto {

        // 1. Visit Strategy
        if (strategy == TransactionStrategy.VISIT) {
            val target = settingsVisitTarget
            val current = card.visitsCount

            // Логика круга визитов
            // Если (current + 1) == target -> достигли цели (награда)
            val isRewardReached = (current + 1) == target
            // Если current >= target -> цикл завершен, начинаем новый (с 1)
            val isCycleRestart = current >= target

            val newVisits = if (isCycleRestart) 1 else current + 1
            // Если цель 0 (выключена), просто растим счетчик без сброса
            val finalVisits = if (target > 0) newVisits else current + 1

            val msg = when {
                target > 0 && isRewardReached -> TransactionCalculationDto.LoyaltyMessage.VISIT_REWARD
                target > 0 && isCycleRestart -> TransactionCalculationDto.LoyaltyMessage.NEXT_REWARD
                else -> TransactionCalculationDto.LoyaltyMessage.VISIT
            }

            return TransactionCalculationDto(
                purchaseAmount = 0.0,
                pointsToSpend = 0.0,
                pointsToAward = 0.0,
                pointsSpent = 0.0,
                moneyPaid = 0.0,
                newBalance = card.balance,
                newVisits = finalVisits,
                message = msg,
                currency = "",
                exchangeRate = 1.0
            )
        }

        // 2. Money Strategies (Денежные стратегии: Баллы/Кэшбэк)
        val safeAmount = LoyaltyFormatter.round(purchaseAmount)

        if (safeAmount < 0) {
            return errorDto(card)
        }

        // A. Списание баллов (SPEND)
        var pointsToSpend = 0.0
        if (strategy == TransactionStrategy.SPEND) {
            // Лимит списания: (Сумма чека * MaxBurn%)
            val maxBurn = LoyaltyFormatter.round(safeAmount * (maxBurnPercentage / 100.0))
            
            // Фактическое списание = Минимум(Баланс карты, Лимит списания)
            pointsToSpend = minOf(card.balance, maxBurn)
        }

        val pointsSpentActual = LoyaltyFormatter.round(pointsToSpend)
        // Остаток к оплате деньгами = Сумма чека - Списанные баллы
        val moneyPaid = LoyaltyFormatter.round(safeAmount - pointsSpentActual)

        // B. Начисление баллов (CHARGE или SPEND с awardOnMixedPayment)
        val currentTier = settingsTiers.find { it.levelIndex == card.tierLevel }
            ?: settingsTiers.maxByOrNull { it.levelIndex }

        // Учитываем флаг awardOnMixedPayment для стратегии SPEND
        val shouldAward = moneyPaid > 0 && (
                strategy == TransactionStrategy.CHARGE ||
                        (strategy == TransactionStrategy.SPEND && awardOnMixedPayment)
                )

        val cashbackEarned = if (shouldAward && currentTier != null) {
            // Кэшбэк начисляется на фактически оплаченную деньгами сумму (moneyPaid)
            LoyaltyFormatter.round(moneyPaid * (currentTier.cashbackPercent / 100.0))
        } else {
            0.0
        }

        // Новый баланс = Старый баланс - Списано + Начислено
        val newBalance = LoyaltyFormatter.round(card.balance - pointsSpentActual + cashbackEarned)

        // C. Формирование сообщения для UI
        var message = when (strategy) {
            TransactionStrategy.CHARGE -> TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE
            TransactionStrategy.SPEND -> TransactionCalculationDto.LoyaltyMessage.TIERED_SPEND
            else -> TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE
        }

        // Уточнение сообщения для SPEND: Хватило ли баллов для покрытия макс. процента?
        if (strategy == TransactionStrategy.SPEND) {
            val burnLimit = LoyaltyFormatter.round(safeAmount * (maxBurnPercentage / 100.0))
            // Если баланс был меньше лимита списания, значит мы списали весь баланс под 0
            val limitedByBalance = card.balance < burnLimit && card.balance <= pointsSpentActual

            if (limitedByBalance && moneyPaid > 0) {
                // "Списано всё, остальное деньгами"
                message = TransactionCalculationDto.LoyaltyMessage.TIERED_ENOUGHT_AMOUNT
            }
        }

        // D. Прогноз повышения уровня (Tier Upgrade Projection)
        // Проверяем, повысится ли уровень после этой покупки
        if (strategy == TransactionStrategy.CHARGE || strategy == TransactionStrategy.SPEND) {
            // Прогнозируемая сумма трат за всё время (LTV)
            val projectedTotal = card.totalSpent + moneyPaid

            val projectedTier = settingsTiers
                .filter { it.threshold <= projectedTotal }
                .maxByOrNull { it.levelIndex }

            if (projectedTier != null && projectedTier.levelIndex > card.tierLevel) {
                message = TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE_CHANGE_TIER
            }
        }

        return TransactionCalculationDto(
            purchaseAmount = safeAmount,
            pointsToSpend = pointsToSpend,
            pointsToAward = cashbackEarned,
            pointsSpent = pointsSpentActual,
            moneyPaid = moneyPaid,
            newBalance = newBalance,
            newVisits = card.visitsCount,
            message = message,
            currency = ""
        )
    }

    private fun errorDto(card: LoyaltyCardDto) = TransactionCalculationDto(
        0.0, 0.0, 0.0, 0.0, 0.0,
        newVisits = card.visitsCount,
        newBalance = card.balance,
        message = TransactionCalculationDto.LoyaltyMessage.TIERED_ERROR_AMOUNT,
        currency = "",
        exchangeRate = 1.0
    )
}

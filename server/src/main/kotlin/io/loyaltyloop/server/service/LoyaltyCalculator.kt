package io.loyaltyloop.server.service

import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import kotlin.math.round

// TODO checked
object LoyaltyCalculator {

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
            val isRewardReached = (current + 1) == target
            val isCycleRestart = current >= target

            val newVisits = if (isCycleRestart) 1 else current + 1
            // Если цель 0 (выключена), просто растим счетчик
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

        // 2. Money Strategies
        val safeAmount = purchaseAmount.round(2)

        if (safeAmount < 0) {
            return errorDto(card,  TransactionCalculationDto.LoyaltyMessage.TIERED_ERROR_AMOUNT)
        }

        // A. Списание
        var pointsToSpend = 0.0
        if (strategy == TransactionStrategy.SPEND) {
            val maxBurn = (safeAmount * (maxBurnPercentage / 100.0)).round(2)
            // Твоя логика: берем минимум от Баланса и Лимита %
            pointsToSpend = minOf(card.balance, maxBurn).round(2)
        }

        val pointsSpentActual = pointsToSpend
        val moneyPaid = (safeAmount - pointsSpentActual).round(2)

        // B. Начисление
        val currentTier = settingsTiers.find { it.levelIndex == card.tierLevel }
            ?: settingsTiers.maxByOrNull { it.levelIndex }

        // Учитываем флаг awardOnMixedPayment
        val shouldAward = moneyPaid > 0 && (
                strategy == TransactionStrategy.CHARGE ||
                        (strategy == TransactionStrategy.SPEND && awardOnMixedPayment)
                )

        val cashbackEarned = if (shouldAward && currentTier != null) {
            (moneyPaid * (currentTier.cashbackPercent / 100.0)).round(2)
        } else {
            0.0
        }

        val newBalance = (card.balance - pointsSpentActual + cashbackEarned).round(2)

        // C. Сообщения
        var message = when (strategy) {
            TransactionStrategy.CHARGE -> TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE
            TransactionStrategy.SPEND -> TransactionCalculationDto.LoyaltyMessage.TIERED_SPEND
            else -> TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE
        }

        // Уточнение для SPEND
        if (strategy == TransactionStrategy.SPEND) {
            val burnLimit = (safeAmount * (maxBurnPercentage / 100.0)).round(2)
            val limitedByBalance = card.balance < burnLimit && card.balance <= pointsSpentActual

            if (limitedByBalance && moneyPaid > 0) {
                message = TransactionCalculationDto.LoyaltyMessage.TIERED_ENOUGHT_AMOUNT
            }
        }

        // D. Прогноз уровня (Tier Upgrade)
        // card.totalSpent и settingsTiers должны быть в одной валюте!
        if (strategy == TransactionStrategy.CHARGE || strategy == TransactionStrategy.SPEND) {
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

    private fun errorDto(card: LoyaltyCardDto, msg: TransactionCalculationDto.LoyaltyMessage) = TransactionCalculationDto(
        0.0, 0.0, 0.0, 0.0, 0.0,
        newVisits = card.visitsCount,
        newBalance = card.balance,
        message = msg,
        currency = "",
        exchangeRate = 1.0
    )

     fun Double.round(decimals: Int = 2): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }
}

package io.loyaltyloop.server.service

import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import kotlin.math.min
import kotlin.math.round

object LoyaltyCalculator {

    fun calculate(
        card: LoyaltyCardDto,
        purchaseAmount: Double,
        maxBurnPercentage: Int,
        settingsVisitTarget: Int,
        settingsTiers: List<LoyaltyTierDto>,
        strategy: TransactionStrategy,
        awardOnMixedPayment: Boolean = false
    ): TransactionCalculationDto {

        // 1. Visit Strategy
        if (strategy == TransactionStrategy.VISIT) {
            val target = settingsVisitTarget
            val current = card.visitsCount

            // Case 8: Если цель уже достигнута, следующий визит сбрасывает счетчик (выдача приза)
            // Предполагаем, что текущая транзакция фиксирует выдачу приза и обнуляет счетчик.
            val newVisits = if (current >= target) 0 else current + 1

            val msg = when {
                newVisits == target -> TransactionCalculationDto.LoyaltyMessage.NEXT_REWARD
                current >= target -> TransactionCalculationDto.LoyaltyMessage.VISIT_REWARD
                else -> TransactionCalculationDto.LoyaltyMessage.VISIT
            }

            return TransactionCalculationDto(
                purchaseAmount = 0.0,
                pointsToSpend = 0.0,
                pointsToAward = 0.0,
                pointsSpent = 0.0,
                moneyPaid = 0.0,
                newBalance = card.balance,
                newVisits = newVisits,
                message = msg
            )
        }

        val safeAmount = purchaseAmount.round(2)

        if (safeAmount < 0) {
            return TransactionCalculationDto(
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                newVisits = card.visitsCount,
                newBalance = card.balance,
                message = TransactionCalculationDto.LoyaltyMessage.TIERED_ERROR_AMOUNT
            )
        }

        var pointsToSpend = 0.0
        if (strategy == TransactionStrategy.SPEND) {
            val maxBurn = (safeAmount * (maxBurnPercentage / 100.0)).round(2)
            pointsToSpend = min(card.balance, maxBurn).round(2)
        }

        val pointsSpentActual = pointsToSpend
        val moneyPaid = (safeAmount - pointsSpentActual).round(2)

        val currentTier =
            settingsTiers.find { it.levelIndex == card.tierLevel } ?: settingsTiers.maxByOrNull { it.levelIndex }

        val shouldAward =
            moneyPaid > 0 && (strategy == TransactionStrategy.CHARGE || (strategy == TransactionStrategy.SPEND && awardOnMixedPayment))

        val cashbackEarned = if (shouldAward) {
            val percent = currentTier?.cashbackPercent ?: 0.0
            (moneyPaid * (percent / 100)).round(2)
        } else {
            0.0
        }

        val newBalance = (card.balance - pointsSpentActual + cashbackEarned).round(2)

        var message = when (strategy) {
            TransactionStrategy.CHARGE -> TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE
            TransactionStrategy.SPEND -> TransactionCalculationDto.LoyaltyMessage.TIERED_SPEND
            TransactionStrategy.VISIT -> TransactionCalculationDto.LoyaltyMessage.VISIT
        }

        if (strategy == TransactionStrategy.SPEND) {
            val burnLimit = (safeAmount * (maxBurnPercentage / 100.0)).round(2)
            val limitedByBalance = card.balance < burnLimit && card.balance <= pointsSpentActual
            if (limitedByBalance && moneyPaid > 0) {
                message = TransactionCalculationDto.LoyaltyMessage.TIERED_ENOUGHT_AMOUNT
            }
        }

        if (strategy == TransactionStrategy.CHARGE) {
            val projectedTotal = card.totalSpent + moneyPaid
            val projectedTier =
                settingsTiers.filter { it.threshold <= projectedTotal }.maxByOrNull { it.levelIndex }
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
            message = message
        )
    }

    private fun Double.round(decimals: Int = 2): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return round(this * multiplier) / multiplier
    }
}

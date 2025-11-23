package io.loyaltyloop.server.service

import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltySettingsDto
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import kotlin.math.min
import kotlin.math.round

object LoyaltyCalculator {

    fun calculate(
        card: LoyaltyCardDto,
        settings: LoyaltySettingsDto,
        purchaseAmount: Double,
        strategy: TransactionStrategy
    ): TransactionCalculationDto {
        
        // 1. Visit Strategy
        if (strategy == TransactionStrategy.VISIT) {
             val target = settings.visitsTarget ?: 6
             val current = card.visitsCount
             
             // Case 8: Если цель уже достигнута, следующий визит сбрасывает счетчик (выдача приза)
             // Предполагаем, что текущая транзакция фиксирует выдачу приза и обнуляет счетчик.
             val newVisits = if (current >= target) 0 else current + 1
             
             val msg = when {
                 newVisits == target -> "Visits: $newVisits/$target. REWARD! 🎉"
                 current >= target -> "Reward Claimed! Cycle Reset."
                 else -> "Visits: $newVisits/$target"
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
        
        // 2. Money Strategies (CHARGE / SPEND)
        val safeAmount = purchaseAmount.round(2)
        
        if (safeAmount < 0) {
             return TransactionCalculationDto(0.0,0.0,0.0,0.0,0.0, card.balance, card.visitsCount, "Invalid Amount")
        }

        var pointsToSpend = 0.0
        if (strategy == TransactionStrategy.SPEND) {
             // Calculate max points to spend automatically
             val maxBurn = safeAmount * (settings.maxBurnPercentage / 100.0)
             pointsToSpend = min(card.balance, maxBurn).round(2)
        }

        var pointsSpentActual = 0.0
        var cashbackEarned = 0.0
        var message = ""

        // Redemption
        if (pointsToSpend > 0) {
            // Validation logic is implicit because we calculated safe max.
            // But if balance is 0, pointsToSpend is 0.
            pointsSpentActual = pointsToSpend
            message += "Redeemed: -$pointsSpentActual "
        }

        // Accrual
        val moneyPaid = (safeAmount - pointsSpentActual).round(2)
        
        if (pointsSpentActual == 0.0 && safeAmount > 0) {
            val currentTier = settings.tiers.find { it.levelIndex == card.tierLevel }
            val percent = currentTier?.cashbackPercent ?: 0.03
            cashbackEarned = (moneyPaid * percent).round(2)
            message += "Earned: +$cashbackEarned"
        } else {
             if (pointsSpentActual > 0) message += "(No cashback)"
        }
        
        val newBalance = (card.balance - pointsSpentActual + cashbackEarned).round(2)
        if (message.isBlank()) message = "Balance: $newBalance"
        else message += ". Balance: $newBalance"
        
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
        return kotlin.math.round(this * multiplier) / multiplier
    }
}

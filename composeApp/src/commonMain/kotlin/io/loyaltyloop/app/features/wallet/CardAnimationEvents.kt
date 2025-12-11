package io.loyaltyloop.app.features.wallet

import io.loyaltyloop.shared.models.LoyaltyCardDto

sealed interface CardAnimationEvent {
    data class BalanceEarned(val amount: Double) : CardAnimationEvent
    data class BalanceSpent(val amount: Double) : CardAnimationEvent
    data class VisitProgress(
        val increment: Int,
        val remainingToReward: Int? = null
    ) : CardAnimationEvent
    data class TierUpgrade(val newLevel: Int) : CardAnimationEvent
    data object RewardUnlocked : CardAnimationEvent
    data object CardCreated : CardAnimationEvent
    data object CardSynced : CardAnimationEvent
    data object CardDeleted : CardAnimationEvent
}

data class CardAnimationMessage(
    val cardId: String,
    val event: CardAnimationEvent,
    val card: LoyaltyCardDto? = null,
    val newBalance: Double? = null,
    val newVisits: Int? = null,
    val tradingPointId: String? = null
)


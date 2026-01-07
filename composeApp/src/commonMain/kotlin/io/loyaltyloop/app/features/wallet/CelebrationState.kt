package io.loyaltyloop.app.features.wallet

import io.loyaltyloop.shared.models.LoyaltyCardDto
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class CelebrationType {
    Earn, Spend, Visit, Reward, Tier, Created
}

@OptIn(ExperimentalTime::class)
data class CelebrationState(
    val id: Long = Clock.System.now().toEpochMilliseconds(),
    val cardId: String,
    val cardName: String,
    val type: CelebrationType,
    val amount: Double? = null,
    val remainingVisits: Int? = null,
    val visitsIncrement: Int? = null,
    val tierLevel: Int? = null,
    val newBalance: Double? = null,
    val newVisits: Int? = null,
    val visitsTarget: Int? = null,
    val dismissAfterMs: Long = 6_400L,
    val tradingPointId: String? = null
) {
    companion object {
        fun from(
            card: LoyaltyCardDto,
            event: CardAnimationEvent,
            newBalance: Double?,
            newVisits: Int?,
            tradingPointId: String?
        ): CelebrationState? {
            return when (event) {
                is CardAnimationEvent.BalanceEarned -> CelebrationState(
                    cardId = card.id,
                    cardName = card.partnerName,
                    type = CelebrationType.Earn,
                    amount = event.amount,
                    newBalance = newBalance ?: card.balance,
                    tradingPointId = tradingPointId
                )

                is CardAnimationEvent.BalanceSpent -> CelebrationState(
                    cardId = card.id,
                    cardName = card.partnerName,
                    type = CelebrationType.Spend,
                    amount = event.amount,
                    newBalance = newBalance ?: card.balance,
                    tradingPointId = tradingPointId
                )

                is CardAnimationEvent.VisitProgress -> CelebrationState(
                    cardId = card.id,
                    cardName = card.partnerName,
                    type = CelebrationType.Visit,
                    visitsIncrement = event.increment,
                    remainingVisits = event.remainingToReward,
                    newVisits = newVisits ?: card.visitsCount,
                    visitsTarget = card.visitsTarget,
                    tradingPointId = tradingPointId
                )

                CardAnimationEvent.RewardUnlocked -> CelebrationState(
                    cardId = card.id,
                    cardName = card.partnerName,
                    type = CelebrationType.Reward,
                    newVisits = newVisits ?: card.visitsCount,
                    visitsTarget = card.visitsTarget,
                    tradingPointId = tradingPointId
                )

                is CardAnimationEvent.TierUpgrade -> CelebrationState(
                    cardId = card.id,
                    cardName = card.partnerName,
                    type = CelebrationType.Tier,
                    tierLevel = event.newLevel,
                    tradingPointId = tradingPointId
                )

                CardAnimationEvent.CardCreated -> CelebrationState(
                    cardId = card.id,
                    cardName = card.partnerName,
                    type = CelebrationType.Created,
                    tradingPointId = tradingPointId
                )

                CardAnimationEvent.CardSynced,
                CardAnimationEvent.CardDeleted -> null
            }
        }
    }
}


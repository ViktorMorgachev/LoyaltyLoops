package io.loyaltyloop.server.service

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardsTable
import io.loyaltyloop.server.repository.RatingRepository
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.LoyaltyCardRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.shared.models.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

// TODO checked
class RatingService(
    private val ratingRepository: RatingRepository,
    private val eventLogger: EventLogger,
    private val config: ApplicationConfig, // Injected config
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val tradingPointRepository: TradingPointRepository
) {
    private val logger = LoggerFactory.getLogger("RatingService")


    // Feature Flag
    private val enableCooldown: Boolean
        get() = config.tryGetString("features.rating.enableCooldown")?.toBoolean() ?: true

    private val tagWeights: Map<ClientRatingTag, Double> = buildTagWeights()

    private fun buildTagWeights(): Map<ClientRatingTag, Double> {
        val defaults = mapOf(
            ClientRatingTag.AGGRESSION to ClientRatingTag.AGGRESSION.penalty,
            ClientRatingTag.NO_PAYMENT to ClientRatingTag.NO_PAYMENT.penalty,
            ClientRatingTag.TIP to  ClientRatingTag.TIP.penalty,
            ClientRatingTag.POLITE to ClientRatingTag.POLITE.penalty,
            ClientRatingTag.FRIENDLY to ClientRatingTag.FRIENDLY.penalty,
            ClientRatingTag.ABUSE to ClientRatingTag.ABUSE.penalty,
            ClientRatingTag.FRAUD to ClientRatingTag.FRAUD.penalty,
            ClientRatingTag.NONE to ClientRatingTag.NONE.penalty
        )
        return defaults.mapValues { (tag, def) ->
            config.tryGetString("rating.tags.client.${tag.name}")?.toDoubleOrNull() ?: def
        }
    }

    suspend fun rateClient(cashierId: String, dto: CreateClientRatingDto, tradingPointId: String, timeZoneCurrency: String): TrustScoreDto {
        val partnerId = tradingPointRepository.getPartnerIdByPointId(tradingPointId)

        if (enableCooldown && ratingRepository.hasCashierRatedUserRecently(cashierId, dto.userId)) {
             throw LoyaltyException(AppErrorCode.RATE_LIMIT_EXCEEDEG, "You have already rated this client today")
        }

        val currentCard =  loyaltyCardRepository.getCardByUserAndPartner(dto.userId, partnerId,timeZoneCurrency )
            ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Client card not found")

        var isIgnored = false
        if (currentCard.trustScore >= 4.5 && currentCard.totalScore > 100 && dto.rating == 1 && !dto.tags.contains(ClientRatingTag.FRAUD)) {
            isIgnored = true
            eventLogger.log(
                type = SystemEventType.WARNING,
                userId = cashierId,
                partnerId = partnerId,
                payload = "Anti-Abuse: Ignored 1-star rating for awesome client (Score: ${currentCard.trustScore}). User: ${dto.userId}"
            )
        }

        ratingRepository.createClientRating(partnerId, cashierId, tradingPointId, dto, isIgnored)

        if (isIgnored) {
            return TrustScoreDto(currentCard.trustScore, currentCard.riskLevel, currentCard.fraudFlag)
        }

        var fraudFlag = currentCard.fraudFlag
        if (dto.tags.contains(ClientRatingTag.FRAUD)) {
            fraudFlag = true
            eventLogger.log(
                type = SystemEventType.WARNING,
                userId = cashierId,
                partnerId = partnerId,
                payload = "Client marked as FRAUD by cashier. User: ${dto.userId}"
            )
        }

        val lastRatings = ratingRepository.getLastRatingsForUser(dto.userId, partnerId, 20)
        val newScore = calculateTrustScore(lastRatings)
        
        updateTrustScore(currentCard.id, newScore, fraudFlag)
        
        val riskLevel = when {
            fraudFlag -> RiskLevel.BLACK
            newScore >= 4.5 -> RiskLevel.GREEN
            newScore >= 3.5 -> RiskLevel.YELLOW
            newScore >= 2.0 -> RiskLevel.ORANGE
            else -> RiskLevel.RED
        }
        
        return TrustScoreDto(newScore, riskLevel, fraudFlag)
    }
    
    suspend fun rateService(userId: String, dto: CreateServiceReviewDto) {
        val partnerId = tradingPointRepository.getPartnerIdByPointId(dto.tradingPointId)
        
        ratingRepository.createServiceReview(partnerId, userId, dto)
    }

    suspend fun updateTrustScore(cardId: String, score: Double, fraud: Boolean) = dbQuery {
        val cardUuid = cardId.toUUID()

        LoyaltyCardsTable.update({ LoyaltyCardsTable.id eq cardUuid }) {
            it[trustScore] = score
            it[fraudFlag] = fraud
            it[totalScore] = totalScore + 1
        }
    }


    private fun calculateTrustScore(ratings: List<RatingRepository.ClientRatingEntity>): Double {
        if (ratings.isEmpty()) return 4.0
        
        var totalScore = 0.0
        
        for (r in ratings) {
            var score = r.rating.toDouble()
            for (tag in r.tags) {
                val weight = if (tag == ClientRatingTag.FRAUD) 0.0 else tagWeights[tag] ?: 0.0
                score += weight
                if (tag == ClientRatingTag.FRAUD) {
                    logger.warn("FRAUD tag detected in rating calculation; weight not applied but alert triggered.")
                }
            }
            totalScore += score
        }
        
        val avg = totalScore / ratings.size
        return avg.coerceIn(0.0, 5.0)
    }
}

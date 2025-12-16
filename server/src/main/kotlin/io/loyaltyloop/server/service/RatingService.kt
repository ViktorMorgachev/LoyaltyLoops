package io.loyaltyloop.server.service

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.loyaltyloop.server.repository.RatingRepository
import io.loyaltyloop.server.repository.TransactionRepository
import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.utils.CardUtils
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.*
import org.slf4j.LoggerFactory

class RatingService(
    private val ratingRepository: RatingRepository,
    private val transactionRepository: TransactionRepository,
    private val eventLogger: EventLogger,
    private val config: ApplicationConfig, // Injected config
    private val cardUtils: CardUtils,
) {
    private val logger = LoggerFactory.getLogger("RatingService")

    // Configuration
    private val MAX_RATINGS_FOR_AVERAGE = 10
    private val RATING_RESET_VISITS = 20
    private val ANTI_ABUSE_RATING_COOLDOWN_HOURS = 12 // 1 rating per cashier per client half day

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

    suspend fun rateClient(cashierId: String, dto: CreateClientRatingDto, timeZoneCurrency: String): TrustScoreDto {
        val partnerId = ratingRepository.getPartnerIdByPointId(dto.tradingPointId)
            ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND, "Trading point not found")

        // 1. Anti-Abuse: Check frequency (1 per day per cashier->user)
        if (enableCooldown && ratingRepository.hasCashierRatedUserRecently(cashierId, dto.userId)) {
             throw LoyaltyException(AppErrorCode.RATE_LIMIT_EXCEEDEG, "You have already rated this client today")
        }
        
        // 2. Anti-Abuse: Outlier Detection
        // If the rating is very low (1) but the client is generally very reliable (Green/VIP) in this partner network,
        // and there is no Fraud tag, we might ignore this as a potential "revenge" rating or anomaly.
        // Rule: If Current Score >= 4.5 (Green) AND New Rating == 1 AND No FRAUD tag -> Ignore

        val currentCard =  cardUtils.getCardByUserAndPartner(dto.userId, partnerId,timeZoneCurrency )
            ?: throw LoyaltyException(AppErrorCode.CARD_NOT_FOUND, "Client card not found")

        var isIgnored = false
        if (currentCard.trustScore >= 4.5 && dto.rating == 1 && !dto.tags.contains(ClientRatingTag.FRAUD)) {
            isIgnored = true
            eventLogger.log(
                type = SystemEventType.WARNING,
                userId = cashierId,
                partnerId = partnerId,
                payload = "Anti-Abuse: Ignored 1-star rating for awesome client (Score: ${currentCard.trustScore}). User: ${dto.userId}"
            )
        }
        
        // 3. Save Rating
        ratingRepository.createClientRating(partnerId, cashierId, dto, isIgnored)
        
        // If ignored, we don't update the score (return old score)
        if (isIgnored) {
            return TrustScoreDto(currentCard.trustScore, currentCard.riskLevel, currentCard.fraudFlag)
        }
        
        // 4. Update Loyalty Card
        // Check for FRAUD tag
        var fraudFlag = currentCard.fraudFlag
        if (dto.tags.contains(ClientRatingTag.FRAUD)) {
            fraudFlag = true
            eventLogger.log(
                type = SystemEventType.WARNING, // Changed from INFO to WARNING
                userId = cashierId,
                partnerId = partnerId,
                payload = "Client marked as FRAUD by cashier. User: ${dto.userId}"
            )
        }
        
        // Calculate new Score
        val lastRatings = ratingRepository.getLastRatingsForUser(dto.userId, partnerId, 20)
        val newScore = calculateTrustScore(lastRatings)
        
        transactionRepository.updateTrustScore(currentCard.id, newScore, fraudFlag)
        
        val riskLevel = when {
            fraudFlag -> RiskLevel.BLACK
            newScore >= 4.5 -> RiskLevel.GREEN
            newScore >= 3.5 -> RiskLevel.YELLOW
            newScore >= 2.0 -> RiskLevel.ORANGE
            else -> RiskLevel.RED
        }
        
        return TrustScoreDto(newScore, riskLevel, fraudFlag)
    }
    
    suspend fun rateService(userId: String, dto: CreateServiceReviewDto): String {
        val partnerId = ratingRepository.getPartnerIdByPointId(dto.tradingPointId)
             ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND, "Trading point not found")
        
        ratingRepository.createServiceReview(partnerId, userId, dto)
        return "review_created" // Placeholder
    }

    
    private fun calculateTrustScore(ratings: List<RatingRepository.ClientRatingEntity>): Double {
        if (ratings.isEmpty()) return 4.0 // Default for new
        
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
        // Clamp result 0.0 .. 5.0
        return avg.coerceIn(0.0, 5.0)
    }
}

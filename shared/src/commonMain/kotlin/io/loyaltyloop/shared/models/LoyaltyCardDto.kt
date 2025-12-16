package io.loyaltyloop.shared.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoyaltyCardDto(
    val id: String,
    val userId: String,
    val partnerId: String,
    val balance: Double,
    val totalSpent: Double,
    val visitsCount: Int,
    val tierLevel: Int,
    val block: CardBlockStatus?,
    val pause: CardPauseStatus?,
    val visitsTarget: Int,
    @SerialName("blockedUntil")
    val legacyBlockedUntil: Long? = block?.until,
    @SerialName("isBlocked")
    val legacyIsBlocked: Boolean = block != null,
    @SerialName("isClosed")
    val legacyIsClosed: Boolean = pause != null,
    @SerialName("closedReason")
    val legacyClosedReason: String? = pause?.reason,
    val partnerName: String,
    val cardColor: String,
    val logoUrl: String?,
    val trustScore: Double,
    val fraudFlag: Boolean,
    val riskLevel: RiskLevel,
    
    // Multi-currency support
    val partnerBaseCurrency: String,
    val estimatedValue: Double, // Value in user's local currency
    val estimatedCurrency: String // User's local currency code
)

@Serializable
data class CardBlockStatus(
    val until: Long,
    val reason: String? = null
)

@Serializable
data class CardPauseStatus(
    val reason: String? = null
)

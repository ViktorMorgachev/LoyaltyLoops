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
    val block: CardBlockStatus? = null,
    val pause: CardPauseStatus? = null,
    val visitsTarget: Int = 10,
    @SerialName("blockedUntil")
    val legacyBlockedUntil: Long? = block?.until,
    @SerialName("isBlocked")
    val legacyIsBlocked: Boolean = block != null,
    @SerialName("isClosed")
    val legacyIsClosed: Boolean = pause != null,
    @SerialName("closedReason")
    val legacyClosedReason: String? = pause?.reason,
    val partnerName: String = "",
    val cardColor: String = "#808080",
    val logoUrl: String? = null
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
package io.loyaltyloop.server.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionWarningDto(
    val partnerId: String,
    val partnerName: String,
    val pointId: String,
    val pointName: String,
    val endDate: Long
)



package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class LoyaltyCardDto(
    val id: String,
    val userId: String,
    val partnerId: String,
    val balance: Double,
    val totalSpent: Double,
    val tierLevel: Int,
    val isBlocked: Boolean,
    val isClosed: Boolean = false
    // Позже добавим сюда название уровня и цвет (из настроек партнера)
)
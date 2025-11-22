package io.loyaltyloop.shared.models

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
    val isBlocked: Boolean,

    val isClosed: Boolean = false,
    val partnerName: String = "",      // "Sierra Coffee"
    val cardColor: String = "#808080", // Цвет карточки
    val logoUrl: String? = null
    // Позже добавим сюда название уровня и цвет (из настроек партнера)
)
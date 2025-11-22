package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class TradingPointType {
    COFFEE_SHOP,
    RESTAURANT,
    RETAIL,     // Магазин
    SERVICE,    // Салон красоты и т.д.
    OTHER
}

@Serializable
data class TradingPointDto(
    val id: String,
    val name: String,
    val active: Boolean,
    val type: TradingPointType,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val inviteCode: String?
)

@Serializable
data class CreateTradingPointRequest(
    val name: String,
    val type: TradingPointType, // JSON: "type": "COFFEE_SHOP"
    val address: String? = null
)
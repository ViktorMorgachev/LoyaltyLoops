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
    val address: String?,
    val type: TradingPointType,
    val latitude: Double?,  // Гео
    val longitude: Double?, // Гео
    val active: Boolean
)
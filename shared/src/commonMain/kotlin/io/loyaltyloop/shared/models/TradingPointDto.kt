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
    val inviteCode: String?,
    val currency: String = "KGS"
)

@Serializable
data class TradingPointDetailsDto(
    val point: TradingPointDto,
    val settings: LoyaltySettingsDto
)

@Serializable
data class CreateTradingPointRequest(
    val name: String,
    val type: TradingPointType, // JSON: "type": "COFFEE_SHOP"
    val address: String? = null,
    val currency: String = "KGS",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val programType: LoyaltyProgramType = LoyaltyProgramType.TIERED_LTV,

    // Для VISITS
    val visitsTarget: Int,       // Например: 10

    // Для TIERED (можно передать базовый процент или список, для простоты возьмем базовый)
    val baseCashback: Double  = 5.0   // Например: 0.05 (5%)
)

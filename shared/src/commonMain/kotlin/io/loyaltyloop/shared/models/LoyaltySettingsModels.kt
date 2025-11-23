package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class LoyaltyProgramType {
    TIERED_LTV,    // Накопительная (3 уровня)
    VISIT_COUNTER, // Счетчик посещений (N-й в подарок)
    HYBRID         // Смешанная (Визиты ИЛИ Бонусы)
}

@Serializable
data class LoyaltyTierDto(
    val levelIndex: Int, // 1, 2, 3
    val name: String,
    val threshold: Double, // Порог входа
    val cashbackPercent: Double // 0.05 = 5%
)

@Serializable
data class LoyaltySettingsDto(
    val settingsId: String,
    val programType: LoyaltyProgramType,
    val tradingPointId: String,
    val tiers: List<LoyaltyTierDto>, // Для TIERED
    val visitsTarget: Int? = null,   // Для VISIT_COUNTER
    val visitsReward: String? = null, // Описание награды

    // Политика сгорания
    val burnBonusesAfterDays: Int? = null, // Сгорание бонусов (дней)
    val downgradeTierAfterDays: Int? = null, // Сброс уровня при неактивности (дней)
    val maxBurnPercentage: Int = 100 // Макс % списания
)

@Serializable
data class UpdateTradingPointRequest(
    val name: String,
    val type: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val currency: String = "KGS",
    val settings: UpdateLoyaltySettingsRequest
)

@Serializable
data class UpdateLoyaltySettingsRequest(
    val programType: LoyaltyProgramType,
    val tiers: List<LoyaltyTierDto>,
    val visitsTarget: Int?,
    val burnBonusesAfterDays: Int? = null,
    val downgradeTierAfterDays: Int? = null,
    val maxBurnPercentage: Int = 100
)

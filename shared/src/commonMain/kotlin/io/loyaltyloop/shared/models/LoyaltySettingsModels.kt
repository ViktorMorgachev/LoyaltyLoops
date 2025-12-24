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
    val loyaltyTier: LoyaltyTier,
    val threshold: Double, // Порог входа
    val cashbackPercent: Double
){
    enum class LoyaltyLevel {
        Base, Silver, Gold
    }
    @Serializable
    data class LoyaltyTier(val level: LoyaltyLevel, val descr: String? = null)
}


fun indexToLoyaltyLevel(index: Int): LoyaltyTierDto.LoyaltyLevel {
    return when (index) {
        1 -> LoyaltyTierDto.LoyaltyLevel.Base
        2 -> LoyaltyTierDto.LoyaltyLevel.Silver
        else -> LoyaltyTierDto.LoyaltyLevel.Gold
    }
}






@Serializable
data class LoyaltySettingsDto(
    val settingsId: String,
    val tradingPointId: String,

    // ЛОКАЛЬНЫЕ настройки (могут отличаться на разных точках)
    val programType: LoyaltyProgramType, // На этой точке копим визиты или деньги?
    val currency: String,                // Валюта этой точки (KGS)

    // ГЛОБАЛЬНЫЕ настройки (Транслируем из Партнера)
    val visitsTarget: Int,               // Общая цель (10)
    val visitsReward: String? = null,    // Описание награды (из настроек точки или общее)
    val tiers: List<LoyaltyTierDto>,     // Общая сетка уровней

    val burnBonusesAfterDays: Int?,      // Общее правило
    val downgradeTierAfterDays: Int?,    // Общее правило

    // Политика списания (обычно локальная, но можно сделать глобальной)
    val maxBurnPercentage: Int,
    val awardOnMixedPayment: Boolean
)

@Serializable
data class UpdateTradingPointRequest(
    val name: String,
    val type: TradingPointType,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val currency: String,
    val settings: UpdateLoyaltySettingsRequest,
    val schedule: WeeklyScheduleDto? = null,
    val temporarilyPaused: Boolean = false,
    val contactPhone: String? = null,
    val contactLink: String? = null,
    val additionalInfo: String? = null,
    val timezone: String = "UTC"
)

@Serializable
data class UpdateLoyaltySettingsRequest(
    val programType: LoyaltyProgramType,
    val maxBurnPercentage: Int = 100,
    val awardOnMixedPayment: Boolean = false
)

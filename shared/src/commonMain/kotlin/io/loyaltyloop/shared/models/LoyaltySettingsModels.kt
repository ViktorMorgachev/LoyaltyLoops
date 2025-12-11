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





@Serializable
data class LoyaltySettingsDto(
    val settingsId: String,
    val programType: LoyaltyProgramType,
    val tradingPointId: String,
    val tiers: List<LoyaltyTierDto>, // Для TIERED
    val visitsTarget: Int,   // Для VISIT_COUNTER
    val visitsReward: String? = null, // Описание награды

    // Политика сгорания
    val burnBonusesAfterDays: Int? = null, // Сгорание бонусов (дней)
    val downgradeTierAfterDays: Int? = null, // Сброс уровня при неактивности (дней)
    val maxBurnPercentage: Int = 100, // Макс % списания
    val awardOnMixedPayment: Boolean = false // Начислять бонусы на оплачиваемую деньгами часть
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
    val additionalInfo: String? = null
)

@Serializable
data class UpdateLoyaltySettingsRequest(
    val programType: LoyaltyProgramType,
    val tiers: List<LoyaltyTierDto>,
    val visitsTarget: Int? = null,
    val burnBonusesAfterDays: Int? = null,
    val downgradeTierAfterDays: Int? = null,
    val maxBurnPercentage: Int = 100,
    val awardOnMixedPayment: Boolean = false
)

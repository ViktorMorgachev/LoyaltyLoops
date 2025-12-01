package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class TradingPointType {
    COFFEE_SHOP,
    RESTAURANT,
    RETAIL,
    SERVICE,
    FLOWERS,
    GIFTS,
    CAKES,
    BARBERSHOP,
    CLOTHING,
    TOYS,
    CAR_RENTAL,
    SCOOTER_RENTAL,
    AUTO_SERVICE,
    TIRE_SERVICE,
    AUTO_PARTS,
    BANK,
    OTHER
}

@Serializable
enum class WeekDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

@Serializable
data class WorkingIntervalDto(
    val opensAt: String, // HH:mm
    val closesAt: String // HH:mm
)

@Serializable
data class WorkingDayDto(
    val day: WeekDay,
    val intervals: List<WorkingIntervalDto> = emptyList()
)

@Serializable
data class WeeklyScheduleDto(
    val timezone: String = "Asia/Bishkek",
    val days: List<WorkingDayDto> = emptyList()
)

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
    val currency: String,
    val schedule: WeeklyScheduleDto? = null,
    val rating: Double? = null,
    val reviewCount: Int = 0,
    val distanceMeters: Double? = null,
    val isOpenNow: Boolean? = null,
    val temporarilyPaused: Boolean = false,
    val contactPhone: String? = null,
    val contactLink: String? = null, // URL (e.g. t.me/..., wa.me/...)
    val additionalInfo: String? = null // Max 20 chars
)

@Serializable
data class TradingPointDetailsDto(
    val point: TradingPointDto,
    val settings: LoyaltySettingsDto
)

@Serializable
data class TradingPointSearchResponse(
    val points: List<TradingPointDto>,
    val total: Int,
    val radiusMeters: Int,
    val limit: Int,
    val hasMore: Boolean
)

@Serializable
data class CreateTradingPointRequest(
    val name: String,
    val type: TradingPointType,
    val address: String,
    val currency: Currency,
    val latitude: Double,
    val longitude: Double,
    val programType: LoyaltyProgramType = LoyaltyProgramType.TIERED_LTV,

    // Для VISITS
    val visitsTarget: Int = 10,       //TODO Вынести в конфиги

    // Для TIERED (можно передать базовый процент или список, для простоты возьмем базовый)
    val baseCashback: Double  = 5.0,
    val awardOnMixedPayment: Boolean = false,
    val schedule: WeeklyScheduleDto? = null,
    val temporarilyPaused: Boolean = false,
    val contactPhone: String? = null,
    val contactLink: String? = null,
    val additionalInfo: String? = null
)

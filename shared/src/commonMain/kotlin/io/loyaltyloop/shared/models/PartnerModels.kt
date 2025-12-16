package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class PartnerStatus {
    PENDING,  // Только создал, на проверке
    ACTIVE,   // Одобрен/Оплачен
    BLOCKED   // Забанен
}

@Serializable
data class CreatePartnerRequest(
    val businessName: String,
    val countryCode: CountryCode = CountryCode.KG,
    val ownerPin: String,
    val color: String? = null,
    val logoUrl: String? = null
)

@Serializable
data class UpdatePartnerRequest(
    val businessName: String,
    val color: String,   // HEX цвет (#FF0000)
    val logoUrl: String?, // Пока просто ссылка текстом
    val burnBonusesDays: Int? = null,
    val downgradeTierDays: Int? = null,
    val defaultVisitsTarget: Int = 10
)

@Serializable
data class UpdatePinRequest(
    val currentPin: String? = null,
    val newPin: String
)

@Serializable
data class ResetPinRequest(
    val confirm: Boolean = false
)

@Serializable
data class PinResetConfirmRequest(
    val token: String,
    val newPin: String
)


@Serializable
data class ExpiringPointDto(
    val pointName: String,
    val endDate: Long
)

@Serializable
data class PartnerEntity(
    val id: String,
    val ownerId: String,
    val businessName: String,
    val countryCode: String,
    val hasPin: Boolean,
    val status: PartnerStatus,
    val logoUrl: String?,
    val color: String,
    val burnBonusesDays: Int?,
    val downgradeTierDays: Int?,
    val defaultVisitsTarget: Int = 10,
    val ownerPhone: String? = null,
    val subscriptionWarnings: List<ExpiringPointDto>? = null
)

@Serializable
data class ChangePartnerStatusRequest(
    val status: PartnerStatus
)

/**
 * Запрос на присоединение к точке (для Кассира)
 */
@Serializable
data class JoinTradingPointRequest(
    val inviteCode: String
)

@Serializable
data class CashierJobEntity(
    val tradingPointId: String,
    val pointName: String,
    val businessName: String
)

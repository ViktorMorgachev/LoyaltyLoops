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
    val countryCode: String // "KG"
)

@Serializable
data class UpdatePartnerRequest(
    val businessName: String,
    val color: String,   // HEX цвет (#FF0000)
    val logoUrl: String?, // Пока просто ссылка текстом
    val burnBonusesDays: Int? = null,
    val downgradeTierDays: Int? = null
)

@Serializable
data class PartnerDto(
    val id: String,
    val businessName: String,
    val countryCode: String,
    val status: PartnerStatus,
    val color: String,
    val logoUrl: String?,
    val burnBonusesDays: Int? = null,
    val downgradeTierDays: Int? = null,
    val ownerPhone: String? = null // NEW: For Admin Panel
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

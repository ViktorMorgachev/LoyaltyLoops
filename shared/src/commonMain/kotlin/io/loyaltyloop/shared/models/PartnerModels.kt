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
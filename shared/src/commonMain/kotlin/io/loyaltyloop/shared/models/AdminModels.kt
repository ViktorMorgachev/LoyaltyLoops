package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class PartnerStatsDto(
    val partnerId: String,
    val pointsCount: Int,
    val cardsCount: Int,
    val transactionsCount: Int
)

@Serializable
data class ChangePointStatusRequest(
    val isActive: Boolean
)
@Serializable
data class AuthSession(
    val id: String,
    val status: String,
    val telegramId: Long?,
    val phone: String?,
    val userId: String?,
    val expiresAt: Long
)

@Serializable
data class CreateManagerInviteRequest(
    val email: String // Просто для логов, инвайт будет кодом
)

@Serializable
data class ManagerInviteDto(
    val code: String,
    val role: UserRole
)

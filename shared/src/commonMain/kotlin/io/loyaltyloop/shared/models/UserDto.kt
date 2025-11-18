package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class UserRole {
    SUPER_ADMIN,
    PARTNER_ADMIN,
    CASHIER,
    CLIENT
}

@Serializable
data class UserDto(
    val id: String,
    val phoneNumber: String,
    val role: UserRole,
    val countryCode: String // "KG", "KZ", etc.
)
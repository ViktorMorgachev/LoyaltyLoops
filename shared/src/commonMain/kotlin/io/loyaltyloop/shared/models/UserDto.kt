package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class UserRole {
    CLIENT,         // Обычный покупатель
    PARTNER_ADMIN,  // Владелец бизнеса (требует PIN)
    CASHIER,        // Сотрудник (вход свободный)
    SUPER_ADMIN     // Ты
}

@Serializable
data class UserDto(
    val id: String,
    val phoneNumber: String,
    val countryCode: String, // "KG", "KZ", etc.
    val firstName: String?,
    val lastName: String? = null,
    val email: String? = null,
    val qrSecret: String,
    val language: String = "ru"
)
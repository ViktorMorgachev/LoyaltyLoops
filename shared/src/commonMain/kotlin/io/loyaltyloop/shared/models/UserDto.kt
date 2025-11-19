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
    val countryCode: String // "KG", "KZ", etc.
)
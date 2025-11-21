package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class UserRole {
    CLIENT,         // Обычный
    CASHIER,        // Сотрудник точки
    PARTNER_ADMIN,  // Владелец бизнеса

    // Системные роли
    PLATFORM_SUPER_ADMIN, // Бог
    PLATFORM_MANAGER      // Менеджер поддержки
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
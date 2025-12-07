package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class UserRole {
    CLIENT,         // Обычный
    CASHIER,        // Сотрудник точки
    PARTNER_ADMIN,  // Владелец бизнеса
    PARTNER_MANAGER, // Менеджер партнера

    // Системные роли
    PLATFORM_SUPER_ADMIN, // Бог
    PLATFORM_SUPER_MANAGER, // Руководитель отдела продаж
    PLATFORM_MANAGER      // Менеджер поддержки / Продаж
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
    val language: String = "ru",
    val isFrozenUntil: Long? = null,
    val isDeleted: Boolean = false
)

@Serializable
data class TestApiErrors(
    val apiError: AppErrorCode
)
package io.loyaltyloop.shared.models

import io.ktor.util.toUpperCasePreservingASCIIRules
import kotlinx.serialization.Serializable

enum class UserRole {
    CLIENT,         // Обычный
    CASHIER,        // Сотрудник точки
    PARTNER_ADMIN,  // Владелец бизнеса
    PARTNER_MANAGER, // Менеджер партнера

    // Системные роли
    PLATFORM_SUPER_ADMIN, // Бог
    PLATFORM_SUPER_MANAGER, // Руководитель отдела продаж
    PLATFORM_MANAGER;   // Менеджер поддержки / Продаж

    fun getDescription(): String {
        return when(this){
            CLIENT, CASHIER -> this.name.lowercase()
            PARTNER_ADMIN -> "Partner"
            PARTNER_MANAGER -> "Partner manager"
            PLATFORM_SUPER_ADMIN -> "Owner"
            PLATFORM_SUPER_MANAGER -> "Super manager"
            PLATFORM_MANAGER -> "Manager"
        }
    }
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
    val createdAt: Long,
    val language: String = "ru",
    val isFrozenUntil: Long? = null,
    val isDeleted: Boolean = false,
    val telegramId: Long? = null
)

@Serializable
data class TestApiErrors(
    val apiError: AppErrorCode
)

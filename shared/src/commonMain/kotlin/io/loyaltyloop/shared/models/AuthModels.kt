package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

// Запрос на вход (просто телефон и код)
@Serializable
data class SendCodeRequest(
    val phone: String // Полный номер (+996555...)
)
@Serializable
data class VerifyCodeRequest(
    val phone: String,
    val code: String
)

// ОТВЕТ СЕРВЕРА (Самое важное)
@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val isNewUser: Boolean,
    // Список мест, куда этот юзер может войти
    val workspaces: List<UserWorkspace>
)

// Описание рабочего места
@Serializable
data class UserWorkspace(
    val id: String,             // ID Партнера или Торговой Точки
    val title: String,          // "Кофейня Sierra" или "Филиал ЦУМ"
    val role: UserRole,         // Кем он там является
    val requirePin: Boolean     // Нужно ли вводить PIN для входа?
)

@Serializable
data class UserProfileResponse(
    val userId: String,
    val phone: String,
    val countryCode: String,
    val firstName: String?,
    val lastName: String?,
    val email: String?,
    val language: String,
    val workspaces: List<UserWorkspace>
)



@Serializable
data class RefreshTokenRequest(val refreshToken: String)
package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

// Запрос на отправку СМС
@Serializable
data class SendCodeRequest(
    val phone: String // Полный номер (+996555...)
)

// Запрос на проверку кода
@Serializable
data class VerifyCodeRequest(
    val phone: String,
    val code: String
)

// Ответ сервера с токеном
@Serializable
data class AuthResponse(
    val token: String,
    val userId: String,
    val isNewUser: Boolean // Чтобы клиент знал, кидать на регистрацию или в профиль
)
package io.loyaltyloop.server.models


enum class AuthSessionStatus {
    PENDING,   // Ждем действия (ввода кода или нажатия в ТГ)
    CONFIRMED, // Успех, можно выдавать токен
    EXPIRED    // Время вышло
}

package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,   // "OK" или "ERROR"
    val db: String,       // "connected" или "disconnected"
    val uptime: Long      // Время работы сервера в мс
)

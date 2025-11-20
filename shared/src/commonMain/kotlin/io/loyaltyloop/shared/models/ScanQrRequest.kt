package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ScanQrRequest(
    val qrContent: String // "loyalty_v1:userid:timestamp"
)

@Serializable
data class ScanQrResponse(
    val userId: String,
    val userPhone: String,
    val cardId: String,
    val currentBalance: Double,
    val tierLevel: Int,
    val isNewCard: Boolean // Чтобы кассир сказал: "О, вы у нас впервые!"
)
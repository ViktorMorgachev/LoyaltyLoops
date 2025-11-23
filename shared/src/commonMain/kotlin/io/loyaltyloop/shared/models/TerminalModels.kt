package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ScanQrRequest(
    val qrContent: String, // "loyalty_v1:userid:timestamp"
    val tradingPointId: String
)

@Serializable
data class ScanQrResponse(
    val userId: String,
    val userPhone: String, // Чтобы кассир мог поздороваться: "Здравствуйте, ...!"
    val firstName: String?, // Имя важнее телефона
    // Данные карты
    val cardId: String,
    val currentBalance: Double, // Для TIERED
    val visitsCount: Int,       // Для VISITS
    // Правила игры (Контекст точки)
    val programType: LoyaltyProgramType, // TIERED или VISITS  или Hybrid
    val visitsTarget: Int? = null,       // Например, 6 (если VISITS)
    val cashbackPercent: Double? = null,  // Например 5 (если TIERED)
    val maxBurnPercentage: Int = 100,     // Макс % оплаты баллами
    val currency: String = "KGS",         // Валюта точки
    val awardOnMixedPayment: Boolean = false, // Начисление бонусов при комбинированном платеже

    val isNewCard: Boolean
)


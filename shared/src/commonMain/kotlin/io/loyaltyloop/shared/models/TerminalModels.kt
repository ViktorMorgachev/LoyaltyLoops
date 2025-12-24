package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ScanQrRequest(
    val qrContent: String, // "loyalty_v1:userid:timestamp"
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
    val visitsTarget: Int,       // Например, 6 (если VISITS)
    val cashbackPercent: Double? = null,  // Например 5 (если TIERED)
    val maxBurnPercentage: Int,     // Макс % оплаты баллами
    val currency: String,        // Валюта точки
    val awardOnMixedPayment: Boolean = false, // Начисление бонусов при комбинированном платеже

    val isNewCard: Boolean,
    
    // Social Rating
    val trustScore: Double,
    val riskLevel: RiskLevel,
    val fraudFlag: Boolean = false
)


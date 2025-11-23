package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionStrategy {
    CHARGE, // Начисление (Покупка)
    SPEND,  // Списание (Оплата баллами)
    VISIT   // Визит
}

@Serializable
data class ProcessTransactionRequest(
    val tradingPointId: String,
    val cardId: String,
    val purchaseAmount: Double = 0.0,
    val strategy: TransactionStrategy
)

@Serializable
enum class TransactionSuccessType {
    VISIT_PROGRESS, // Args: current, target
    VISIT_REWARD,   // Args: target (Reward Claimed)
    POINTS_EARNED,  // Args: earned
    POINTS_SPENT,   // Args: spent
    POINTS_SPENT_EARNED, // Args: spent, earned
    BALANCE_INFO,   // Args: balance
    SUCCESS_DEFAULT // No args
}

@Serializable
data class TransactionResult(
    val cardId: String,
    val newBalance: Double,
    val newVisits: Int,
    val type: TransactionSuccessType = TransactionSuccessType.SUCCESS_DEFAULT,
    val args: List<String> = emptyList()
)

@Serializable
data class TransactionHistoryDto(
    val id: String,
    val timestamp: Long,
    val pointName: String, // Название точки для UI
    val type: String,      // VISIT, EARN, SPEND
    val amount: Double,    // Сумма покупки
    val pointsDelta: Double, // Баллы (+/-)
    val visitsDelta: Int     // Визиты (+/-)
)

@Serializable
data class CalculateTransactionRequest(
    val tradingPointId: String,
    val cardId: String,
    val purchaseAmount: Double = 0.0,
    val strategy: TransactionStrategy
)

@Serializable
data class TransactionCalculationDto(
    val purchaseAmount: Double,
    val pointsToSpend: Double,
    val pointsToAward: Double, // Сколько будет начислено
    val pointsSpent: Double,   // Сколько реально спишется
    val moneyPaid: Double,     // Сколько денег платить
    val newBalance: Double,    // Прогноз баланса
    val newVisits: Int, // Прогноз визитов
    val message: LoyaltyMessage
){
    enum class LoyaltyMessage{
        VISIT,
        VISIT_REWARD,
        NEXT_REWARD,
        TIERED_ENOUGHT_AMOUNT,
        TIERED_ERROR_AMOUNT,
        TIERED_CHARGE,
        TIERED_CHARGE_CHANGE_TIER,
        TIERED_SPEND

    }
}

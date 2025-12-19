package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class TransactionStrategy {
    CHARGE, // Начисление (Покупка)
    SPEND,  // Списание (Оплата баллами)
    VISIT   // Визит
}

enum class TransactionTypeHistory {
    VISIT,
    EARN,
    CHARGE,
    VISIT_REWARD,
    VISIT_PROGRESS,
    POINTS_SPENT_EARNED,
    POINTS_SPENT,
    POINTS_EARNED,
    BALANCE_INFO,
    EXPIRATION,      // Полное сгорание бонусов
    TIER_DOWNGRADE
}

@Serializable
data class ProcessTransactionRequest(
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
    SUCCESS_DEFAULT, // No args
    CARD_CREATED,
    CARD_UPDATED,
    CARD_DELETED
}

@Serializable
data class TransactionResult(
    val cardId: String,
    val newBalance: Double,
    val currency: String,
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
    val visitsDelta: Int,    // Визиты (+/-)
    val currency: String? = null // Валюта транзакции
)

@Serializable
data class CalculateTransactionRequest(
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
    val message: LoyaltyMessage,
    val currency: String,      // Пример: "KGS", "USD", "RUB"
    val exchangeRate: Double = 1.0  // Пример: 85.0 (Курс Base -> Terminal)
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

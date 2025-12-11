package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class CashierDailyStatsDto(
    val transactionsCount: Int,
    val totalRevenue: Double,
    val pointsAwarded: Double,
    val pointsSpent: Double,
    val visitsRecorded: Int,
    val currency: String
)


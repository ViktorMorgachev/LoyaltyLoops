package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class RevenueChartPoint(
    val date: String, // ISO date or label
    val revenue: Double,
    val transactionsCount: Int
)

@Serializable
data class AnalyticsResponse(
    val totalRevenue: Double,
    val totalTransactions: Int,
    val averageCheck: Double,
    val chartData: List<RevenueChartPoint>
)

@Serializable
enum class AnalyticsPeriod {
    WEEK, MONTH, SIX_MONTHS, YEAR
}


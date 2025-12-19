package io.loyaltyloop.server.service

import io.loyaltyloop.server.repository.HistoryRepository
import io.loyaltyloop.server.repository.LoyaltyCardRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.repository.PartnerStaffRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CashierDailyStatsDto
import io.loyaltyloop.shared.models.Employer
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.shared.models.AnalyticsResponse
import io.loyaltyloop.shared.models.PartnerStatsDto

// TODO checked
class AnalyticsService(
    private val partnerStaffRepository: PartnerStaffRepository,
    private val historyRepository: HistoryRepository,
    private val tradingPointRepository: TradingPointRepository,
    private val loyaltyCardRepository: LoyaltyCardRepository,
    private val partnerRepository: PartnerRepository,
) {
    suspend fun getAnalytics(
        partnerId: String,
        period: io.loyaltyloop.shared.models.AnalyticsPeriod,
        timezone: String
    ): AnalyticsResponse {
        val partner = partnerRepository.getPartnerByIdOrThrow(partnerId)
        val zoneId = try {
            java.time.ZoneId.of(timezone)
        } catch (e: Exception) {
            java.time.ZoneId.of("UTC")
        }

        val now = System.currentTimeMillis()
        val (from, grouping) = when (period) {
            io.loyaltyloop.shared.models.AnalyticsPeriod.WEEK -> (now - 6 * 24 * 3600 * 1000L) to GroupingType.DAY // 7 days including today
            io.loyaltyloop.shared.models.AnalyticsPeriod.MONTH -> (now - 29 * 24 * 3600 * 1000L) to GroupingType.DAY // 30 days
            io.loyaltyloop.shared.models.AnalyticsPeriod.SIX_MONTHS -> (now - 180L * 24 * 3600 * 1000L) to GroupingType.MONTH
            io.loyaltyloop.shared.models.AnalyticsPeriod.YEAR -> (now - 365L * 24 * 3600 * 1000L) to GroupingType.MONTH
        }

        val transactions = historyRepository.getTransactionsForAnalytics(partner!!.id, from, now)

        val groupedMap = transactions.groupBy {
            formatDate(it.timestamp, grouping, zoneId)
        }

        val totalRevenue = transactions.sumOf { it.amount }
        val totalTransactions = transactions.size
        val averageCheck = if (totalTransactions > 0) totalRevenue / totalTransactions else 0.0

        val labels = generateDateLabels(from, now, grouping, zoneId)

        val chartData = labels.map { label ->
            val pointsInLabel = groupedMap[label] ?: emptyList()
            io.loyaltyloop.shared.models.RevenueChartPoint(
                date = label,
                revenue = pointsInLabel.sumOf { it.amount },
                transactionsCount = pointsInLabel.size
            )
        }

        return AnalyticsResponse(
            totalRevenue = totalRevenue,
            totalTransactions = totalTransactions,
            averageCheck = averageCheck,
            chartData = chartData
        )
    }

    private enum class GroupingType { DAY, MONTH }

    private fun formatDate(timestamp: Long, type: GroupingType, zoneId: java.time.ZoneId): String {
        val instant = java.time.Instant.ofEpochMilli(timestamp)
        val ldt = java.time.LocalDateTime.ofInstant(instant, zoneId)

        return when (type) {
            GroupingType.DAY -> ldt.toLocalDate().toString()
            GroupingType.MONTH -> "${ldt.year}-${ldt.monthValue.toString().padStart(2, '0')}"
        }
    }

    private fun generateDateLabels(from: Long, to: Long, type: GroupingType, zoneId: java.time.ZoneId): List<String> {
        val labels = mutableListOf<String>()
        var current = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(from), zoneId)
        val end = java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(to), zoneId)

        while (!current.isAfter(end)) {
            val label = when (type) {
                GroupingType.DAY -> current.toLocalDate().toString()
                GroupingType.MONTH -> "${current.year}-${current.monthValue.toString().padStart(2, '0')}"
            }

            if (labels.lastOrNull() != label) {
                labels.add(label)
            }

            current = when (type) {
                GroupingType.DAY -> current.plusDays(1)
                GroupingType.MONTH -> current.plusMonths(1)
            }
        }
        return labels
    }

    suspend fun getPartnerCashiersWithStats(partnerId: String): List<Employer> {
        val cashiers = partnerStaffRepository.getAllCashiers(partnerId)
        if (cashiers.isEmpty()) return emptyList()

        val cashierIds = cashiers.map { it.userId }
        val stats = historyRepository.getLifetimeStatsForCashiers(cashierIds)

        return cashiers.map { cashier ->
            val (revenue, count) = stats[cashier.userId] ?: (0.0 to 0)
            cashier.copy(
                revenue = revenue,
                transactionsCount = count
            )
        }
    }

    suspend fun getPartnerStats(partnerId: String): PartnerStatsDto {
        val pointsCount = tradingPointRepository.countByPartnerId(partnerId)
        val cardsCount = loyaltyCardRepository.countByPartnerId(partnerId)
        val transactionsCount = historyRepository.countByPartnerId(partnerId)

        return PartnerStatsDto(
            partnerId = partnerId,
            pointsCount = pointsCount,
            cardsCount = cardsCount,
            transactionsCount = transactionsCount
        )
    }

    suspend fun getCashierDailyStats(
        userId: String,    // ID Юзера (из токена)
        pointId: String    // ID Точки (из хедера X-Workspace-Id)
    ): CashierDailyStatsDto {

        val point = tradingPointRepository.getPointById(pointId) // DTO

        val zoneId = try {
            java.time.ZoneId.of(point.timezone)
        } catch (e: Exception) {
            java.time.ZoneId.of("UTC")
        }

        // 2. Вычисляем начало дня ПО ВРЕМЕНИ ТОЧКИ
        val now = nowUtc()
        val todayStartLocal = java.time.LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()

        val staffUuid = partnerStaffRepository.getCassierById(userId, pointId)
            ?: throw LoyaltyException(AppErrorCode.FORBIDDEN, "User is not active staff at this point")

        val staffId = staffUuid.toString()

        return historyRepository.getCashierStats(
            cashierId = staffId,
            tradingPointId = pointId,
            from = todayStartLocal.toEpochMilli(),
            to = now.toUtcMillis()
        )
    }
}

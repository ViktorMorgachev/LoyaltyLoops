package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.utils.toTransactionHistoryDto
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcLocalDateTime
import io.loyaltyloop.shared.models.CashierDailyStatsDto
import io.loyaltyloop.shared.models.TransactionHistoryDto
import io.loyaltyloop.shared.models.TransactionTypeHistory
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import java.math.BigDecimal
import java.time.LocalDateTime

// TODO checked
class HistoryRepository {
    suspend fun recordTransaction(
        userId: String,
        pointId: String,
        cashierId: String,
        type: String,
        amount: Double,
        pointsDelta: Double,
        visitsDelta: Int,
        currency: String,
        exchangeRate: Double,
        pointsBaseValue: Double,
        updatedAt: LocalDateTime
    ) = dbQuery {
        val userUuid = userId.toUUID()
        val pointUuid = pointId.toUUID()
        val cashierUuid = cashierId.toUUID()

        TransactionsHistoryTable.insert {
            it[this.user] = userUuid
            it[this.tradingPoint] = pointUuid
            it[this.cashier] = cashierUuid
            it[this.type] = TransactionTypeHistory.valueOf(type)
            it[this.amount] = BigDecimal.valueOf(amount)
            it[this.pointsDelta] = BigDecimal.valueOf(pointsDelta)
            it[this.visitsDelta] = visitsDelta
            it[this.currency] = currency
            it[this.createdAt] = updatedAt
            it[this.exchangeRateSnapshot] = BigDecimal.valueOf(exchangeRate)
            it[this.pointsBaseValue] = BigDecimal.valueOf(pointsBaseValue)
        }
    }

    suspend fun getTransactionsForAnalytics(
        partnerId: String,
        from: Long,
        to: Long
    ): List<TransactionHistoryDto> = dbQuery {
        val partnerUuid = partnerId.toUUID()

        val fromDate = from.toUtcLocalDateTime()
        val toDate = to.toUtcLocalDateTime()

        TransactionsHistoryTable
            .innerJoin(TradingPointsTable)
            .selectAll()
            .where {
                (TradingPointsTable.partner eq partnerUuid) and
                        (TransactionsHistoryTable.createdAt greaterEq fromDate) and
                        (TransactionsHistoryTable.createdAt lessEq toDate)
            }
            .orderBy(TransactionsHistoryTable.createdAt, SortOrder.DESC)
            .map { it.toTransactionHistoryDto() }
    }

    suspend fun getHistoryForPartner(
        partnerId: String,
        limit: Int = 50,
        offset: Long = 0
    ): List<TransactionHistoryDto> = dbQuery {
        val partnerUuid = partnerId.toUUID()

        TransactionsHistoryTable
            .innerJoin(TradingPointsTable)
            .selectAll()
            .where {
                TradingPointsTable.partner eq partnerUuid
            }
            .orderBy(TransactionsHistoryTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { it.toTransactionHistoryDto() }
    }

    suspend fun getCashierStats(
        cashierId: String,
        tradingPointId: String,
        from: Long,
        to: Long
    ): CashierDailyStatsDto = dbQuery {
        val cashierUuid = cashierId.toUUID()
        val tradingPointUuid = tradingPointId.toUUID()

        val fromDate = from.toUtcLocalDateTime()
        val toDate = to.toUtcLocalDateTime()

        val rangeCondition = (TransactionsHistoryTable.cashier eq cashierUuid) and
                (TransactionsHistoryTable.createdAt greaterEq fromDate) and
                (TransactionsHistoryTable.createdAt lessEq toDate) and
                (TransactionsHistoryTable.tradingPoint eq tradingPointUuid)

        val currency = TransactionsHistoryTable
            .slice(TransactionsHistoryTable.currency)
            .select { rangeCondition }
            .limit(1)
            .map { it[TransactionsHistoryTable.currency] }
            .singleOrNull() ?: ""

        val statsRow = TransactionsHistoryTable
            .slice(
                TransactionsHistoryTable.id.count(),
                TransactionsHistoryTable.amount.sum(),
                TransactionsHistoryTable.visitsDelta.sum()
            )
            .select { rangeCondition }
            .singleOrNull()

        val awarded = TransactionsHistoryTable
            .slice(TransactionsHistoryTable.pointsDelta.sum())
            .select {
                rangeCondition and (TransactionsHistoryTable.pointsDelta greater BigDecimal.ZERO)
            }
            .singleOrNull()
            ?.getOrNull(TransactionsHistoryTable.pointsDelta.sum())
            ?: BigDecimal.ZERO

        val spent = TransactionsHistoryTable
            .slice(TransactionsHistoryTable.pointsDelta.sum())
            .select {
                rangeCondition and (TransactionsHistoryTable.pointsDelta less BigDecimal.ZERO)
            }
            .singleOrNull()
            ?.getOrNull(TransactionsHistoryTable.pointsDelta.sum())
            ?: BigDecimal.ZERO

        CashierDailyStatsDto(
            transactionsCount = statsRow?.getOrNull(TransactionsHistoryTable.id.count())?.toInt() ?: 0,
            totalRevenue = statsRow?.getOrNull(TransactionsHistoryTable.amount.sum())?.toDouble() ?: 0.0,
            pointsAwarded = awarded.toDouble(),
            pointsSpent = spent.abs().toDouble(),
            visitsRecorded = statsRow?.getOrNull(TransactionsHistoryTable.visitsDelta.sum()) ?: 0,
            currency = currency
        )
    }

    suspend fun countByPartnerId(partnerId: String): Int = dbQuery {
        TransactionsHistoryTable
            .innerJoin(TradingPointsTable)
            .select { TradingPointsTable.partner eq partnerId.toUUID() }
            .count()
            .toInt()
    }

    suspend fun getLifetimeStatsForCashiers(cashierIds: List<String>): Map<String, Pair<Double, Int>> = dbQuery {
        val cashierUuids = cashierIds.map { it.toUUID() }

        val sumAmount = TransactionsHistoryTable.amount.sum()
        val countTx = TransactionsHistoryTable.id.count()

        TransactionsHistoryTable
            .slice(TransactionsHistoryTable.cashier, sumAmount, countTx)
            .select { TransactionsHistoryTable.cashier inList cashierUuids }
            .groupBy(TransactionsHistoryTable.cashier)
            .associate { row ->
                val cId = row[TransactionsHistoryTable.cashier]?.value.toString()
                val rev = row[sumAmount]?.toDouble() ?: 0.0
                val cnt = row[countTx].toInt()
                cId to (rev to cnt)
            }
    }
}
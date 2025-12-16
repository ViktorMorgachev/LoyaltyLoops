package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.service.ExchangeRateService
import io.loyaltyloop.shared.models.CardBlockStatus
import io.loyaltyloop.shared.models.CardPauseStatus
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.TransactionHistoryDto
import io.loyaltyloop.shared.models.CashierDailyStatsDto
import io.loyaltyloop.shared.models.Currency
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.count
import java.util.UUID

@Suppress("TooManyFunctions")
class TransactionRepository {

    // ОПТИМИЗИРОВАННАЯ ВЕРСИЯ (SQL Aggregation)
    suspend fun getCashierStats(cashierId: String, from: Long, to: Long): CashierDailyStatsDto = dbQuery {
        // 1. Быстро получаем валюту точки (Limit 1)
        val currency = TradingPointsTable
            .join(TransactionsHistoryTable, JoinType.INNER, TradingPointsTable.id, TransactionsHistoryTable.tradingPointId)
            .slice(TradingPointsTable.currency)
            .select { TransactionsHistoryTable.cashierId eq cashierId }
            .limit(1)
            .map { it[TradingPointsTable.currency] }
            .singleOrNull() ?: ""

        // 2. Основная агрегация (Count, Revenue, Visits)
        val statsRow = TransactionsHistoryTable
            .slice(
                TransactionsHistoryTable.id.count(),
                TransactionsHistoryTable.amount.sum(),
                TransactionsHistoryTable.visitsDelta.sum()
            )
            .select {
                (TransactionsHistoryTable.cashierId eq cashierId) and
                        (TransactionsHistoryTable.timestamp greaterEq from) and
                        (TransactionsHistoryTable.timestamp lessEq to)
            }
            .firstOrNull()

        // 3. Агрегация баллов (Awarded / Spent) отдельными запросами
        // (Это эффективнее, чем грузить List, и проще, чем сложные Case When в Exposed)

        val basePointsQuery = TransactionsHistoryTable.slice(TransactionsHistoryTable.pointsDelta.sum())

        val awarded = basePointsQuery.select {
            (TransactionsHistoryTable.cashierId eq cashierId) and
                    (TransactionsHistoryTable.timestamp greaterEq from) and
                    (TransactionsHistoryTable.timestamp lessEq to) and
                    (TransactionsHistoryTable.pointsDelta greater 0.0)
        }.singleOrNull()?.getOrNull(TransactionsHistoryTable.pointsDelta.sum()) ?: 0.0

        val spent = basePointsQuery.select {
            (TransactionsHistoryTable.cashierId eq cashierId) and
                    (TransactionsHistoryTable.timestamp greaterEq from) and
                    (TransactionsHistoryTable.timestamp lessEq to) and
                    (TransactionsHistoryTable.pointsDelta less 0.0)
        }.singleOrNull()?.getOrNull(TransactionsHistoryTable.pointsDelta.sum()) ?: 0.0

        CashierDailyStatsDto(
            transactionsCount = statsRow?.getOrNull(TransactionsHistoryTable.id.count())?.toInt() ?: 0,
            totalRevenue = statsRow?.getOrNull(TransactionsHistoryTable.amount.sum()) ?: 0.0,
            pointsAwarded = awarded,
            pointsSpent = -spent, // Инвертируем, так как в базе они хранятся с минусом
            visitsRecorded = statsRow?.getOrNull(TransactionsHistoryTable.visitsDelta.sum()) ?: 0,
            currency = currency
        )
    }

     fun incrementVisits(cardId: String)  {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            with(SqlExpressionBuilder) {
                it[visitsCount] = visitsCount + 1
            }
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

     fun addCashback(cardId: String, cashback: Double, spentAmount: Double) {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            with(SqlExpressionBuilder) {
                it[balance] = balance + cashback
                it[totalSpent] = totalSpent + spentAmount
            }
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

     fun updateTier(cardId: String, level: Int)  {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            it[tierLevel] = level
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

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
        pointsBaseValue: Double
    ) = dbQuery {
        TransactionsHistoryTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.userId] = userId
            it[this.tradingPointId] = pointId
            it[this.cashierId] = cashierId
            it[this.type] = type
            it[this.amount] = amount
            it[this.pointsDelta] = pointsDelta
            it[this.visitsDelta] = visitsDelta
            it[this.timestamp] = System.currentTimeMillis()
            it[this.currency] = currency
            it[this.exchangeRateSnapshot] = exchangeRate
            it[this.pointsBaseValue] = pointsBaseValue
        }
    }

    suspend fun getHistoryForPartner(partnerId: String): List<TransactionHistoryDto> = dbQuery {
        TransactionsHistoryTable.join(
            TradingPointsTable,
            JoinType.INNER,
            onColumn = TransactionsHistoryTable.tradingPointId,
            otherColumn = TradingPointsTable.id
        )
            .selectAll()
            .where { TradingPointsTable.partnerId eq partnerId }
            .orderBy(TransactionsHistoryTable.timestamp, SortOrder.DESC)
            .map { row ->
                TransactionHistoryDto(
                    id = row[TransactionsHistoryTable.id],
                    timestamp = row[TransactionsHistoryTable.timestamp],
                    pointName = row[TradingPointsTable.name],
                    type = row[TransactionsHistoryTable.type],
                    amount = row[TransactionsHistoryTable.amount],
                    pointsDelta = row[TransactionsHistoryTable.pointsDelta],
                    visitsDelta = row[TransactionsHistoryTable.visitsDelta],
                    currency = row[TransactionsHistoryTable.currency]
                )
            }
    }

    suspend fun getTransactionsForAnalytics(partnerId: String, from: Long, to: Long): List<TransactionHistoryDto> = dbQuery {
        TransactionsHistoryTable.join(
            TradingPointsTable,
            JoinType.INNER,
            onColumn = TransactionsHistoryTable.tradingPointId,
            otherColumn = TradingPointsTable.id
        )
            .selectAll()
            .where {
                (TradingPointsTable.partnerId eq partnerId) and
                        (TransactionsHistoryTable.timestamp greaterEq from) and
                        (TransactionsHistoryTable.timestamp lessEq to)
            }
            .map { row ->
                TransactionHistoryDto(
                    id = row[TransactionsHistoryTable.id],
                    timestamp = row[TransactionsHistoryTable.timestamp],
                    pointName = row[TradingPointsTable.name],
                    type = row[TransactionsHistoryTable.type],
                    amount = row[TransactionsHistoryTable.amount],
                    pointsDelta = row[TransactionsHistoryTable.pointsDelta],
                    visitsDelta = row[TransactionsHistoryTable.visitsDelta],
                    currency = row[TransactionsHistoryTable.currency]
                )
            }
    }

    suspend fun updateTrustScore(cardId: String, score: Double, fraud: Boolean) = dbQuery {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            it[trustScore] = score
            it[fraudFlag] = fraud
        }
    }
}
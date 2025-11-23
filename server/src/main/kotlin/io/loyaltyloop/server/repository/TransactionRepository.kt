package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.TransactionHistoryDto
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.util.UUID

class TransactionRepository {

    // Получить карту по ID (для транзакций)
    suspend fun getCardById(cardId: String): LoyaltyCardDto? = dbQuery {
        LoyaltyCardTable.select { LoyaltyCardTable.id eq cardId }
            .map { rowToCardDto(it) }
            .singleOrNull()
    }

    // Увеличить счетчик визитов (Legacy, лучше использовать updateVisits)
    suspend fun incrementVisits(cardId: String, amount: Int = 1) = dbQuery {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            with(SqlExpressionBuilder) {
                it.update(visitsCount, visitsCount + amount)
            }
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

    // Обновить счетчик визитов (установить конкретное значение)
    suspend fun updateVisits(cardId: String, newCount: Int) = dbQuery {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            it[visitsCount] = newCount
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

    // Начислить кешбэк и обновить LTV
    suspend fun addCashback(cardId: String, cashback: Double, spentAmount: Double) = dbQuery {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            with(SqlExpressionBuilder) {
                it.update(balance, balance + cashback)
                it.update(totalSpent, totalSpent + spentAmount)
            }
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

    // Обновить уровень
    suspend fun updateTier(cardId: String, level: Int) = dbQuery {
        LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
            it[tierLevel] = level
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

    // Записать историю транзакции
    suspend fun recordTransaction(
        userId: String,
        pointId: String,
        cashierId: String,
        type: String,
        amount: Double,
        pointsDelta: Double = 0.0,
        visitsDelta: Int = 0
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
        }
    }

    // Получить историю для партнера (Владельца бизнеса)
    suspend fun getHistoryForPartner(partnerId: String): List<TransactionHistoryDto> = dbQuery {
        TransactionsHistoryTable.join(
            TradingPointsTable,
            JoinType.INNER,
            onColumn = TransactionsHistoryTable.tradingPointId,
            otherColumn = TradingPointsTable.id
        )
            .select { TradingPointsTable.partnerId eq partnerId }
            .orderBy(TransactionsHistoryTable.timestamp, SortOrder.DESC)
            .map { row ->
                TransactionHistoryDto(
                    id = row[TransactionsHistoryTable.id],
                    timestamp = row[TransactionsHistoryTable.timestamp],
                    pointName = row[TradingPointsTable.name],
                    type = row[TransactionsHistoryTable.type],
                    amount = row[TransactionsHistoryTable.amount],
                    pointsDelta = row[TransactionsHistoryTable.pointsDelta],
                    visitsDelta = row[TransactionsHistoryTable.visitsDelta]
                )
            }
    }

    // Получить транзакции для аналитики за период
    suspend fun getTransactionsForAnalytics(partnerId: String, from: Long, to: Long): List<TransactionHistoryDto> = dbQuery {
        TransactionsHistoryTable.join(
            TradingPointsTable,
            JoinType.INNER,
            onColumn = TransactionsHistoryTable.tradingPointId,
            otherColumn = TradingPointsTable.id
        )
            .select {
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
                    visitsDelta = row[TransactionsHistoryTable.visitsDelta]
                )
            }
    }

    private fun rowToCardDto(row: ResultRow): LoyaltyCardDto {
        return LoyaltyCardDto(
            id = row[LoyaltyCardTable.id],
            userId = row[LoyaltyCardTable.userId],
            partnerId = row[LoyaltyCardTable.partnerId],
            balance = row[LoyaltyCardTable.balance],
            totalSpent = row[LoyaltyCardTable.totalSpent],
            tierLevel = row[LoyaltyCardTable.tierLevel],
            isBlocked = row[LoyaltyCardTable.isBlocked],
            isClosed = row[LoyaltyCardTable.isClosed],
            partnerName = "", 
            cardColor = "#000000",
            logoUrl = null,
            visitsCount = row[LoyaltyCardTable.visitsCount]
        )
    }
}

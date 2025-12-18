package io.loyaltyloop.server.utils

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.blockStatus
import io.loyaltyloop.server.database.tables.pauseStatus
import io.loyaltyloop.server.service.ExchangeRateService
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.RiskLevel
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class CardUtils(
    private val exchangeRateService: ExchangeRateService,
) {

    // 1. Получение списка карт
    suspend fun getUserCards(userId: String, estimatedCurrency: String): List<LoyaltyCardDto> {
        // Шаг А: Быстро забираем "сырые" данные из БД (только SQL)
        val rawRows = dbQuery {
            LoyaltyCardTable
                .join(PartnersTable, JoinType.INNER, LoyaltyCardTable.partnerId, PartnersTable.id)
                .selectAll()
                .where { LoyaltyCardTable.userId eq userId }
                .toList() // Превращаем в список сразу, чтобы закрыть транзакцию
        }

        // Шаг Б: Обогащаем данными (Redis, вычисления) вне транзакции БД
        // map - это уже обычная коллекция, здесь можно вызывать suspend функции безопасно
        return rawRows.map { row ->
            mapRowToDto(row, estimatedCurrency)
        }
    }

    // 2. Получение одной карты по ID
    suspend fun getCardByID(cardId: String, estimatedCurrency: String): LoyaltyCardDto? {
        val row = dbQuery {
            LoyaltyCardTable
                .join(PartnersTable, JoinType.INNER, LoyaltyCardTable.partnerId, PartnersTable.id)
                .selectAll()
                .where { LoyaltyCardTable.id eq cardId }
                .singleOrNull()
        } ?: return null

        return mapRowToDto(row, estimatedCurrency)
    }

    // 3. Получение карты по юзеру и партнеру
    suspend fun getCardByUserAndPartner(userId: String, partnerId: String, estimatedCurrency: String): LoyaltyCardDto? {
        val row = dbQuery {
            LoyaltyCardTable
                .join(PartnersTable, JoinType.INNER, LoyaltyCardTable.partnerId, PartnersTable.id)
                .selectAll()
                .where { (LoyaltyCardTable.userId eq userId) and (LoyaltyCardTable.partnerId eq partnerId) }
                .singleOrNull()
        } ?: return null

        return mapRowToDto(row, estimatedCurrency)
    }

    // --- Private Helper (Единая логика маппинга) ---
    private suspend fun mapRowToDto(row: ResultRow, estimatedCurrency: String): LoyaltyCardDto {
        // Берем данные Партнера НАПРЯМУЮ из строки (спасибо JOIN)
        // Нам не нужно делать select query
        val partnerBaseCurrency = row[PartnersTable.baseCurrency]
        val partnerVisitsTarget = row[PartnersTable.defaultVisitsTarget]
        val partnerName = row[PartnersTable.businessName]
        val partnerColor = row[PartnersTable.color]
        val partnerLogo = row[PartnersTable.logoUrl]

        // Баланс из БД
        val balance = row[LoyaltyCardTable.balance]

        // Получаем курс (может сходить в Redis/DB/API)
        // Вызываем это уже ВНЕ транзакции основного запроса карт
        val rate = exchangeRateService.getRate(partnerBaseCurrency, estimatedCurrency)

        // Считаем примерную стоимость
        val estimatedVal = if (estimatedCurrency != partnerBaseCurrency) {
            (balance * rate).round(2)
        } else {
            0.0
        }

        val score = row[LoyaltyCardTable.trustScore]
        val fraud = row[LoyaltyCardTable.fraudFlag]

        val risk = when {
            fraud -> RiskLevel.BLACK
            score >= 4.5 -> RiskLevel.GREEN
            score >= 3.5 -> RiskLevel.YELLOW
            score >= 2.0 -> RiskLevel.ORANGE
            else -> RiskLevel.RED
        }

        return LoyaltyCardDto(
            id = row[LoyaltyCardTable.id],
            userId = row[LoyaltyCardTable.userId],
            partnerId = row[LoyaltyCardTable.partnerId],
            balance = balance,
            totalSpent = row[LoyaltyCardTable.totalSpent],
            tierLevel = row[LoyaltyCardTable.tierLevel],
            block = row.blockStatus(), // Твои extension методы
            pause = row.pauseStatus(),
            partnerName = partnerName,
            cardColor = partnerColor,
            logoUrl = partnerLogo,
            visitsTarget = partnerVisitsTarget,
            visitsCount = row[LoyaltyCardTable.visitsCount],
            trustScore = score,
            fraudFlag = fraud,
            riskLevel = risk,
            partnerBaseCurrency = partnerBaseCurrency,
            estimatedValue = estimatedVal,
            estimatedCurrency = estimatedCurrency
        )
    }

    // Вставка новой карты (тут без изменений, но вызов простой)
    suspend fun insertNewCard(userId: String, partnerID: String) = dbQuery {
        LoyaltyCardTable.insert {
            it[this.userId] = userId
            it[this.partnerId] = partnerID
            it[balance] = 0.0
            it[totalSpent] = 0.0
            it[tierLevel] = 1
            it[visitsCount] = 0
            it[trustScore] = 4.0
            it[fraudFlag] = false
            it[lastActivityAt] = System.currentTimeMillis()
        }
    }

    // Хелпер для округления
    private fun Double.round(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}

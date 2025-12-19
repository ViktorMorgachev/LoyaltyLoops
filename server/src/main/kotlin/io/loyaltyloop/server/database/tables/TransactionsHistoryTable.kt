package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.TransactionTypeHistory

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime
import java.math.BigDecimal

/**
 * Таблица Истории Транзакций (Журнал операций).
 * Главный источник правды для аудита, аналитики и финансовых сверок.
 *
 * **Архитектурные паттерны:**
 * 1. **Неизменность (Immutability):**
 *    Транзакции никогда не обновляются (`UPDATE`) и не удаляются (`DELETE`).
 *    При возврате создается новая транзакция с типом `REFUND`.
 *
 * 2. **Историческая целостность (SET_NULL):**
 *    Критически важный момент: `onDelete = ReferenceOption.SET_NULL`.
 *    Если Пользователь, Точка или Кассир будут удалены из базы,
 *    запись о финансовой операции **должна остаться**.
 *    Мы просто теряем прямую ссылку, но данные о суммах и датах сохраняются.
 *
 * 3. **Snapshotting (Снимки состояния):**
 *    - `exchangeRateSnapshot`: Курс валют меняется каждый час. Мы сохраняем курс
 *      именно на момент совершения сделки, чтобы через год отчет сошелся до копейки.
 *    - `currency`: Валюта оплаты (KGS, RUB) фиксируется здесь.
 *
 * 4. **Мультивалютность (Base Value):**
 *    Поле `pointsBaseValue` хранит эквивалент операции в Базовой Валюте системы (USD).
 *    Это позволяет строить общие отчеты и графики, не мучаясь с конвертацией на лету.
 */
// TODO checked
object TransactionsHistoryTable : UUIDTable("transactions_history") {
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE).nullable()
    val tradingPoint = reference("trading_point_id", TradingPointsTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val cashier = reference("cashier_id", PartnerStaffTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val type = enumerationByName("type", 20, TransactionTypeHistory::class)
    val amount = decimal("amount", 10, 2).default(BigDecimal.ZERO)
    val pointsDelta = decimal("points_delta", 10, 2).default(BigDecimal.ZERO)
    val visitsDelta = integer("visits_delta").default(0)
    val currency = varchar("currency", 3).default("USD")
    val exchangeRateSnapshot = decimal("exchange_rate_snapshot", 19, 6).default(BigDecimal.ONE)
    val pointsBaseValue = decimal("points_base_value", 10, 2).default(BigDecimal.ZERO)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(isUnique = false, user, createdAt)       // История клиента
        index(isUnique = false, tradingPoint, createdAt) // Z-отчет точки
        index(isUnique = false, cashier, createdAt)    // Эффективность кассира
    }
}



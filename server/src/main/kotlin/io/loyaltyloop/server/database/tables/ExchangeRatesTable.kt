package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Справочник актуальных курсов валют.
 * Является "Single Source of Truth" для калькуляции кросс-курсовых транзакций.
 *
 * **Важные архитектурные моменты:**
 * 1. **Natural Key (Естественный ключ):**
 *    Мы не используем UUID. Уникальность записи гарантируется парой валют
 *    (fromCurrency + toCurrency). Невозможно создать дубликат "USD -> KGS".
 *
 * 2. **Финансовая точность:**
 *    Используется `Decimal(19, 6)` вместо Double. Это критично дляслабых валют (например, UZS), где важны 4-6 знаков после запятой,
 *    чтобы избежать артефактов округления при больших суммах.
 *
 * 3. **Обновление:**
 *    Таблица обновляется фоновым Job-ом раз в 4 часа из внешнего API.
 */
// TODO checked
object ExchangeRatesTable : Table("exchange_rates") {
    val fromCurrency = varchar("from_currency", 3)
    val toCurrency = varchar("to_currency", 3)
    val rate = decimal("rate", 19, 6)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    override val primaryKey = PrimaryKey(fromCurrency, toCurrency)
}

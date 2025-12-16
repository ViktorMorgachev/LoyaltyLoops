package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object ExchangeRatesTable : Table("exchange_rates") {
    // Составной ключ: ИЗ какой валюты -> В какую
    val fromCurrency = varchar("from_currency", 3) // Напр. "USD" (Base)
    val toCurrency = varchar("to_currency", 3)     // Напр. "KGS" (Terminal)
    
    // Множитель. Пример: 1 USD = 86.5 KGS. Rate = 86.5
    val rate = double("rate") 
    
    // Время последнего обновления (для мониторинга актуальности)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(fromCurrency, toCurrency)
}

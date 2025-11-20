package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object LoyaltySettingsTable : Table("loyalty_settings") {
    val id = varchar("id", 50)

    // Владелец (для проверки прав доступа)
    val partnerId = varchar("partner_id", 50)
        .references(PartnersTable.id, onDelete = ReferenceOption.CASCADE)

    // --- ОБЯЗАТЕЛЬНАЯ СВЯЗЬ 1:1 ---
    // uniqueIndex гарантирует, что у одной точки не будет двух настроек
    val tradingPointId = varchar("trading_point_id", 50)
        .references(TradingPointsTable.id, onDelete = ReferenceOption.CASCADE)
        .uniqueIndex()
    // -----------------------------------------

    // Тип программы (TIERED_LTV / VISIT_COUNTER)
    val programType = varchar("program_type", 20)

    // Настройки Visits
    val visitsTarget = integer("visits_target").nullable()
    val visitsReward = varchar("visits_reward", 100).nullable()

    // Настройки Expiration
    val inactivityDays = integer("inactivity_days").nullable()
    val visitsResetValue = integer("visits_reset_value").default(0)

    override val primaryKey = PrimaryKey(id)
}
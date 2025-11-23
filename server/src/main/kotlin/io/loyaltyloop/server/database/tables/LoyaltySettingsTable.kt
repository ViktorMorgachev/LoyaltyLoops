package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object LoyaltySettingsTable : Table("loyalty_settings") {
    val id = varchar("id", 50)

    // Владелец (для проверки прав доступа)
    val partnerId = varchar("partner_id", 50)
        .references(PartnersTable.id, onDelete = ReferenceOption.CASCADE)

    // --- ОБЯЗАТЕЛЬНАЯ СВЯЗЬ 1:1 ---
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
    val burnBonusesDays = integer("burn_bonuses_days").nullable()     // Сгорание баллов
    val downgradeTierDays = integer("downgrade_tier_days").nullable() // Сброс уровня
    val maxBurnPercentage = integer("max_burn_percentage").default(100)

    override val primaryKey = PrimaryKey(id)
}

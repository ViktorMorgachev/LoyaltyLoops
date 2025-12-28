package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.LoyaltyProgramType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Настройки программы лояльности для конкретной Торговой Точки.
 * Связь 1-к-1.
 *
 * **Архитектура:**
 * 1. **Гибкость:** Тип программы (`programType`) и параметры списания (`maxBurn`)
 *    настраиваются на уровне Точки. Это позволяет в одном филиале только копить баллы,
 *    а в другом — тратить.
 *
 * 2. **Единая карта (Visits):**
 *    Цель визитов (`visitsTarget`) хранится **только** в `PartnersTable`.
 *    Это гарантирует, что штамп-карта клиента выглядит одинаково во всей сети заведений.
 */
// TODO checked
object LoyaltySettingsTable : UUIDTable("loyalty_settings") {
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val tradingPoint = reference("trading_point_id", TradingPointsTable, onDelete = ReferenceOption.CASCADE)
        .uniqueIndex()
    val programType = enumerationByName("program_type", 20, LoyaltyProgramType::class)

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val visitsReward = varchar("visits_reward", 100).nullable()
    val maxBurnPercentage = integer("max_burn_percentage").default(100)
    val awardOnMixedPayment = bool("award_on_mixed_payment").default(false)

    init {
        index(isUnique = false, partner)
    }
}

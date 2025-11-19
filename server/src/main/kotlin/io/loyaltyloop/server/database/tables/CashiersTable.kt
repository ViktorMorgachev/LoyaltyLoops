package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object CashiersTable : Table("cashiers") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50)
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val tradingPointId = varchar("trading_point_id", 50).references(TradingPointsTable.id)

    // Дублируем partnerId для быстрого поиска всех сотрудников бизнеса
    val partnerId = varchar("partner_id", 50).references(PartnersTable.id)
    val isActive = bool("is_active").default(true)
    val canRefund = bool("can_refund").default(false)

    override val primaryKey = PrimaryKey(id)
}
package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object TradingPointsTable : Table("trading_points") {
    val id = varchar("id", 50)
    val partnerId = varchar("partner_id", 50).references(PartnersTable.id)
    val name = varchar("name", 100)

    // ДЛЯ СОТРУДНИКОВ: Код для присоединения (например "JOIN-123")
    val inviteCode = varchar("invite_code", 20).nullable().uniqueIndex()

    val isActive = bool("is_active").default(false) // Оплачено?

    override val primaryKey = PrimaryKey(id)
}
package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object LoyaltyTiersTable : Table("loyalty_tiers") {
    val id = varchar("id", 50)

    // Ссылка на родительскую настройку
    val settingsId = varchar("settings_id", 50)
        .references( LoyaltySettingsTable.id, onDelete = ReferenceOption.CASCADE)

    val levelIndex = integer("level_index")
    val name = varchar("name", 50)
    val threshold = double("threshold").default(0.0)
    val cashbackPercent = double("cashback_percent").default(0.0)

    override val primaryKey = PrimaryKey(id)
}
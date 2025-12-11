package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object ClientRatingsTable : Table("client_ratings") {
    val id = varchar("id", 50)
    val partnerId = varchar("partner_id", 50).index()
    val tradingPointId = varchar("trading_point_id", 50)
    val cashierId = varchar("cashier_id", 50)
    val userId = varchar("user_id", 50).index()
    val rating = integer("rating")
    val tags = text("tags").nullable() // Comma separated enum names
    val isIgnored = bool("is_ignored").default(false)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}

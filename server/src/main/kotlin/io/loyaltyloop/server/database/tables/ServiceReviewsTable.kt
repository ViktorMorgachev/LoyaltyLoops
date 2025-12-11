package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object ServiceReviewsTable : Table("service_reviews") {
    val id = varchar("id", 50)
    val partnerId = varchar("partner_id", 50).index()
    val tradingPointId = varchar("trading_point_id", 50).index()
    val userId = varchar("user_id", 50)
    val rating = integer("rating")
    val tags = text("tags").nullable()
    val comment = text("comment").nullable()
    val isReadByOwner = bool("is_read_by_owner").default(false)
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}

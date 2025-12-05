package io.loyaltyloop.server.database

import org.jetbrains.exposed.sql.Table

object SystemEventsTable : Table("system_events") {
    val id = varchar("id", 50)
    val type = varchar("type", 50)
    val userId = varchar("user_id", 50).nullable().index()
    val userPhone = varchar("user_phone", 20).nullable().index()
    val partnerId = varchar("partner_id", 50).nullable().index()
    val payload = text("payload").nullable()
    val timestamp = long("created_at").index()

    override val primaryKey = PrimaryKey(id)
}


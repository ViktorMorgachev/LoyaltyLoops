package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object WaitlistTable : Table("waitlist") {
    val id = varchar("id", 50).databaseGenerated()
    val email = varchar("email", 255).uniqueIndex()
    val createdAt = long("created_at").clientDefault { System.currentTimeMillis() }

    override val primaryKey = PrimaryKey(id)
}


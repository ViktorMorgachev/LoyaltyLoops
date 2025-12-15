package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object AuthSessionsTable : Table("auth_sessions") {
    val id = varchar("id", 50)
    val status = varchar("status", 20) // PENDING, CONFIRMED, EXPIRED
    val telegramId = long("telegram_id").nullable()
    val phone = varchar("phone", 20).nullable()
    val userId = varchar("user_id", 50).nullable()
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(id)
}


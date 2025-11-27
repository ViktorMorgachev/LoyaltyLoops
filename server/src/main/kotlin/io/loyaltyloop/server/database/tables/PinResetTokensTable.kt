package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table


object PinResetTokensTable : Table("pin_reset_tokens") {
    val id = varchar("id", 50)
    val userId = varchar("user_id", 50)
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val tokenHash = varchar("token_hash", 128)
    val expiresAt = long("expires_at")
    val usedAt = long("used_at").nullable()

    override val primaryKey = PrimaryKey(id)
}


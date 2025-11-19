package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.Table

object RefreshTokensTable : Table("refresh_tokens") {
    // Замени text("token") на varchar с большим запасом
    val token = varchar("token", 2048)

    val userId = varchar("user_id", 50).references(UsersTable.id)
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(token)
}
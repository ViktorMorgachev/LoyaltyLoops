package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

private const val PLATFORM_LENGTH = 16
private const val ROLE_LENGTH = 32
private const val WORKSPACE_LENGTH = 64
private const val TOKEN_LENGTH = 512

object DeviceTokensTable : Table("device_tokens") {
    val id = uuid("id").autoGenerate()
    val userId = reference("user_id", UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val platform = varchar("platform", PLATFORM_LENGTH)
    val role = varchar("role", ROLE_LENGTH)
    val workspaceId = varchar("workspace_id", WORKSPACE_LENGTH).default("")
    val token = varchar("token", TOKEN_LENGTH)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(
            columns = arrayOf(userId, platform, role, workspaceId)
        )
    }
}


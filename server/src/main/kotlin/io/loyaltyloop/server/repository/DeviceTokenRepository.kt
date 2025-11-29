package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.DeviceTokensTable
import io.loyaltyloop.shared.models.DevicePlatform
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.update
import java.util.UUID

class DeviceTokenRepository {

    suspend fun upsertToken(
        userId: String,
        platform: DevicePlatform,
        role: UserRole,
        workspaceId: String?,
        token: String
    ) = dbQuery {
        val timestamp = System.currentTimeMillis()

        val updated = DeviceTokensTable.update({
            (DeviceTokensTable.userId eq userId) and
                (DeviceTokensTable.platform eq platform.name) and
                (DeviceTokensTable.role eq role.name) and
                (DeviceTokensTable.workspaceId eq workspaceKey(workspaceId))
        }) {
            it[this.token] = token
            it[this.updatedAt] = timestamp
        }

        if (updated == 0) {
            DeviceTokensTable.insertIgnore {
                it[id] = UUID.randomUUID()
                it[this.userId] = userId
                it[this.platform] = platform.name
                it[this.role] = role.name
                it[this.workspaceId] = workspaceKey(workspaceId)
                it[this.token] = token
                it[this.updatedAt] = timestamp
            }
        }
    }

    suspend fun deleteToken(
        userId: String,
        platform: DevicePlatform,
        role: UserRole,
        workspaceId: String?
    ) = dbQuery {
        DeviceTokensTable.deleteWhere {
            (DeviceTokensTable.userId eq userId) and
                (DeviceTokensTable.platform eq platform.name) and
                (DeviceTokensTable.role eq role.name) and
                (DeviceTokensTable.workspaceId eq workspaceKey(workspaceId))
        }
    }

    private fun workspaceKey(workspaceId: String?): String = workspaceId ?: ""
}


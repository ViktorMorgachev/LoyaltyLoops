package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.AuthSessionsTable
import io.loyaltyloop.shared.models.AuthSession
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.util.UUID


class AuthSessionRepository {

    suspend fun createSession(ttlMs: Long): String = dbQuery {
        val uuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        AuthSessionsTable.insert {
            it[id] = uuid
            it[status] = "PENDING"
            it[createdAt] = now
            it[expiresAt] = now + ttlMs
        }
        uuid
    }

    suspend fun getSession(uuid: String): AuthSession? = dbQuery {
        AuthSessionsTable.selectAll().where { AuthSessionsTable.id eq uuid }
            .map {
                AuthSession(
                    id = it[AuthSessionsTable.id],
                    status = it[AuthSessionsTable.status],
                    telegramId = it[AuthSessionsTable.telegramId],
                    phone = it[AuthSessionsTable.phone],
                    userId = it[AuthSessionsTable.userId],
                    expiresAt = it[AuthSessionsTable.expiresAt]
                )
            }.singleOrNull()
    }

    suspend fun confirmSession(uuid: String, telegramId: Long, phone: String, userId: String?) = dbQuery {
        AuthSessionsTable.update({ AuthSessionsTable.id eq uuid }) {
            it[status] = "CONFIRMED"
            it[this.telegramId] = telegramId
            it[this.phone] = phone
            it[this.userId] = userId
        }
    }

    suspend fun cleanupExpiredSessions() = dbQuery {
        val now = System.currentTimeMillis()
        AuthSessionsTable.deleteWhere {
            AuthSessionsTable.expiresAt less now
        }
    }
}


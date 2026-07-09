package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.AuthSessionsTable
import io.loyaltyloop.server.models.AuthSessionStatus
import io.loyaltyloop.server.service.email.ConsoleEmailService
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.AuthSession
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.sql.Connection
import java.time.temporal.ChronoUnit

// TODO checked
class AuthSessionRepository {

    suspend fun createSession(ttlMs: Long): String = dbQuery {
        val now = nowUtc()
        val expirationTime = now.plus(ttlMs, ChronoUnit.MILLIS)

        val newId = AuthSessionsTable.insertAndGetId {
            it[status] = AuthSessionStatus.PENDING
            it[expiresAt] = expirationTime
        }
        newId.value.toString()
    }

    suspend fun getSession(uuid: String): AuthSession? = dbQuery {
        val sessionUuid = uuid.toUUID()

        AuthSessionsTable.selectAll()
            .where { AuthSessionsTable.id eq sessionUuid }
            .map { row ->
                AuthSession(
                    id = row[AuthSessionsTable.id].value.toString(),
                    status = row[AuthSessionsTable.status].name,
                    telegramId = row[AuthSessionsTable.telegramId],
                    phone = row[AuthSessionsTable.phone],
                    userId = row[AuthSessionsTable.user]?.value?.toString(),
                    expiresAt = row[AuthSessionsTable.expiresAt].toUtcMillis()
                )
            }
            .singleOrNull()
    }

    suspend fun cleanupExpiredSessions() = dbQuery {
        val now = nowUtc()

        AuthSessionsTable.deleteWhere {
            AuthSessionsTable.expiresAt less now
        }
    }

    suspend fun confirmSession(uuid: String, telegramId: Long, phone: String, userId: String): Boolean  = dbQuery {
        val sessionUuid = uuid.toUUID()
        val userUuid = userId.toUUID()
        val updatedCount = AuthSessionsTable.update({ AuthSessionsTable.id eq sessionUuid }) {
            it[status] = AuthSessionStatus.CONFIRMED

            it[this.telegramId] = telegramId
            it[this.phone] = phone
            it[user] = userUuid
        }
        updatedCount > 0
    }
}


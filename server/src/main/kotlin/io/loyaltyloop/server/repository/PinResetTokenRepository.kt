package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PinResetTokensTable
import io.loyaltyloop.server.utils.SecurityUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

data class PinResetToken(
    val id: String,
    val userId: String,
    val tokenHash: String,
    val expiresAt: Long,
    val usedAt: Long?
)

class PinResetTokenRepository {

    suspend fun createToken(userId: String, token: String, expiresAt: Long): String = dbQuery {
        val id = UUID.randomUUID().toString()
        PinResetTokensTable.insert {
            it[this.id] = id
            it[this.userId] = userId
            it[this.tokenHash] = SecurityUtils.hashValue(token)
            it[this.expiresAt] = expiresAt
            it[this.usedAt] = null
        }
        id
    }

    suspend fun findValidToken(token: String): PinResetToken? = dbQuery {
        val hash = SecurityUtils.hashValue(token)
        val now = System.currentTimeMillis()
        val condition = (PinResetTokensTable.tokenHash eq hash) and
            (PinResetTokensTable.usedAt.isNull()) and
            (PinResetTokensTable.expiresAt greaterEq now)

        PinResetTokensTable
            .selectAll()
            .where { condition }
            .map {
                PinResetToken(
                    id = it[PinResetTokensTable.id],
                    userId = it[PinResetTokensTable.userId],
                    tokenHash = it[PinResetTokensTable.tokenHash],
                    expiresAt = it[PinResetTokensTable.expiresAt],
                    usedAt = it[PinResetTokensTable.usedAt]
                )
            }
            .singleOrNull()
    }

    suspend fun markUsed(tokenId: String) = dbQuery {
        PinResetTokensTable.update({ PinResetTokensTable.id eq tokenId }) {
            it[usedAt] = System.currentTimeMillis()
        }
    }

    suspend fun revokeAll(userId: String) = dbQuery {
        PinResetTokensTable.deleteWhere { PinResetTokensTable.userId eq userId }
    }
}


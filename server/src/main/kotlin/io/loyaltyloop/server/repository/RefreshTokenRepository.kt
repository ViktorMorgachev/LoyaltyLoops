package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.slf4j.LoggerFactory

// TODO checked
class RefreshTokenRepository {

    private val logger = LoggerFactory.getLogger(AuthSessionRepository::class.java)

    suspend fun saveRefreshToken(
        token: String, userId: String,
        expiresAtTimestamp: Long,
        userAgent: String? = null,
        ipAddress: String? = null
    ) = dbQuery {
        val userUuid = userId.toUUID()
        val expiresAtDate = expiresAtTimestamp.toUtcLocalDateTime()

        RefreshTokensTable.insert {
            it[this.token] = token
            it[this.userAgent] = userAgent
            it[this.ipAddress] = ipAddress
            it[user] = userUuid
            it[expiresAt] = expiresAtDate
        }
    }

    suspend fun findUserIdByToken(token: String): String? = dbQuery {
        RefreshTokensTable
            .slice(RefreshTokensTable.user)
            .select { RefreshTokensTable.token eq token }
            .singleOrNull()
            ?.get(RefreshTokensTable.user)?.value?.toString()
    }

    suspend fun deleteRefreshToken(token: String) = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.token eq token }
    }


    suspend fun cleanupExpiredTokens() = dbQuery {
        val now = nowUtc()

       val deletedTokens = RefreshTokensTable.deleteWhere {
            RefreshTokensTable.expiresAt less now
        }
        logger.info("cleanupExpiredTokens: tokens: $deletedTokens")
    }
}

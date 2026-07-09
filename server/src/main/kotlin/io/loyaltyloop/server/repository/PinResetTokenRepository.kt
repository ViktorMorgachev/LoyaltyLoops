package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PinResetTokensTable
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcLocalDateTime
import io.loyaltyloop.server.utils.toUtcMillis
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

// TODO checked
data class PinResetToken(
    val id: String,
    val partnerId: String,
    val tokenHash: String,
    val expiresAt: Long,
    val usedAt: Long?
)

class PinResetTokenRepository {
    suspend fun createToken(partnerId: String, token: String, expiresAt: Long): String = dbQuery {
        val partnerUuid = partnerId.toUUID()
        val expiresAtDate = expiresAt.toUtcLocalDateTime()

        PinResetTokensTable.deleteWhere {
            (PinResetTokensTable.partner eq partnerUuid)
        }

        val newId = PinResetTokensTable.insertAndGetId {
            it[partner] = partnerUuid
            it[tokenHash] = SecurityUtils.hashValue(token)
            it[this.expiresAt] = expiresAtDate
            it[usedAt] = null
        }

        newId.value.toString()
    }

    suspend fun findValidToken(token: String): PinResetToken? = dbQuery {
        val hash = SecurityUtils.hashValue(token)
        val now = nowUtc()

        PinResetTokensTable
            .selectAll()
            .where {
                (PinResetTokensTable.tokenHash eq hash) and
                        (PinResetTokensTable.usedAt.isNull()) and
                        (PinResetTokensTable.expiresAt greater now)
            }
            .map { row ->
                PinResetToken(
                    id = row[PinResetTokensTable.id].value.toString(),
                    partnerId = row[PinResetTokensTable.partner].value.toString(), // [FIX]
                    tokenHash = row[PinResetTokensTable.tokenHash],
                    expiresAt = row[PinResetTokensTable.expiresAt].toUtcMillis(),
                    usedAt = row[PinResetTokensTable.usedAt]?.toUtcMillis()
                )
            }
            .singleOrNull()
    }

    suspend fun markUsed(tokenId: String): Boolean = dbQuery {
        val tokenUuid = tokenId.toUUID()
        val updated = PinResetTokensTable.update({ PinResetTokensTable.id eq tokenUuid }) {
            it[usedAt] = nowUtc()
        }
        updated > 0
    }


    suspend fun revokeAll(partnerId: String) = dbQuery {
        val partnerId = partnerId.toUUID()
        PinResetTokensTable.deleteWhere {
            PinResetTokensTable.partner eq partnerId
        }
    }
}


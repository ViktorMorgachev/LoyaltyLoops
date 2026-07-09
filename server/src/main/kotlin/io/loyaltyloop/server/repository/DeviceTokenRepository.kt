package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.DeviceTokensTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.shared.models.DevicePlatform
import io.loyaltyloop.shared.models.UserRole
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.booleanLiteral
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.sql.Connection
import java.util.UUID

//TODO Checked
class DeviceTokenRepository {

    suspend fun upsertToken(
        userId: String,
        platform: DevicePlatform,
        role: UserRole,
        workspaceId: String?,
        token: String
    ) = dbQuery {
        val userUuid = userId.toUUID()
        val timestamp = nowUtc()

        var partnerUuid: UUID? = null
        var pointUuid: UUID? = null

        if (!workspaceId.isNullOrBlank() && workspaceId != "platform") {

            val wsUuid = try { UUID.fromString(workspaceId) } catch (_: Exception) { null }

            if (wsUuid != null) {
                when (role) {

                    UserRole.CASHIER -> {
                        pointUuid = wsUuid

                        partnerUuid = TradingPointsTable
                            .slice(TradingPointsTable.partner)
                            .select { TradingPointsTable.id eq pointUuid }
                            .singleOrNull()
                            ?.get(TradingPointsTable.partner)?.value
                    }

                    UserRole.PARTNER_ADMIN, UserRole.PARTNER_MANAGER -> {
                        partnerUuid = wsUuid
                        pointUuid = null // Они не привязаны к конкретной кассе в контексте пушей
                    }

                    else -> {
                        partnerUuid = null
                        pointUuid = null
                    }
                }
            }
        }

        // Try update first
        val updated = DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
            it[user] = userUuid
            it[this.platform] = platform
            it[activeRole] = role
            it[partner] = partnerUuid
            it[tradingPoint] = pointUuid
            it[updatedAt] = timestamp
        }

        // If not updated, insert (ignoring conflict if it happened in between)
        if (updated == 0) {
            try {
                DeviceTokensTable.insert {
                    it[this.token] = token
                    it[user] = userUuid
                    it[this.platform] = platform
                    it[activeRole] = role
                    it[partner] = partnerUuid
                    it[tradingPoint] = pointUuid
                    it[updatedAt] = timestamp
                }
            } catch (_: Exception) {
                // If insert failed (likely unique constraint violation due to race condition), 
                // try update one last time
                DeviceTokensTable.update({ DeviceTokensTable.token eq token }) {
                    it[user] = userUuid
                    it[this.platform] = platform
                    it[activeRole] = role
                    it[partner] = partnerUuid
                    it[tradingPoint] = pointUuid
                    it[updatedAt] = timestamp
                }
            }
        }
    }

    suspend fun deleteToken(
        userId: String,
        platform: DevicePlatform,
        role: UserRole,
        workspaceId: String?
    ) = newSuspendedTransaction(Dispatchers.IO, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
        val userUuid = userId.toUUID()

        val wsUuid = workspaceId?.let {
            try { UUID.fromString(it) } catch (_: Exception) { null }
        }

        DeviceTokensTable.deleteWhere {
            val baseCondition = (user eq userUuid) and
                    (this.platform eq platform) and
                    (activeRole eq role)

            val contextCondition = when (role) {

                UserRole.CASHIER -> {
                    if (wsUuid != null) {
                        tradingPoint eq wsUuid
                    } else {
                        tradingPoint.isNotNull()
                    }
                }

                UserRole.PARTNER_ADMIN, UserRole.PARTNER_MANAGER -> {
                    if (wsUuid != null) {
                        partner eq wsUuid
                    } else {
                        partner.isNotNull()
                    }
                }

                UserRole.CLIENT -> {
                    (partner.isNull()) and (tradingPoint.isNull())
                }

                else -> booleanLiteral(true)
            }

            baseCondition and contextCondition
        }
    }
    suspend fun deleteTokenExact(token: String) = newSuspendedTransaction(Dispatchers.IO, transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) {
        DeviceTokensTable.deleteWhere { DeviceTokensTable.token eq token }
    }


}


package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.hours

class LoyaltyEngineService {

    private val logger = LoggerFactory.getLogger("LoyaltyEngine")

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            logger.info("Loyalty Engine started")
            // Initial delay to let server start up
            delay(10000) 
            
            while (isActive) {
                try {
                    runExpirationCycle()
                } catch (e: Exception) {
                    logger.error("Error in expiration cycle", e)
                }
                delay(24.hours) 
            }
        }
    }

    private suspend fun runExpirationCycle() = dbQuery {
        logger.info("Running expiration cycle...")
        val now = System.currentTimeMillis()

        // 1. Get all expiration rules: Map<PartnerId, BurnDays>
        val rules = PartnersTable
            .selectAll().where { PartnersTable.burnBonusesDays.isNotNull() }.associate {
                it[PartnersTable.id] to it[PartnersTable.burnBonusesDays]!!
            }

        var expiredCount = 0

        rules.forEach { (partnerId, days) ->
            val threshold = now - (days * 24 * 60 * 60 * 1000L)

            // 2. Find cards to expire
            val cards = LoyaltyCardTable.select {
                (LoyaltyCardTable.partnerId eq partnerId) and
                (LoyaltyCardTable.lastActivityAt less threshold) and
                (LoyaltyCardTable.balance greater 0.0)
            }.map { 
                Triple(it[LoyaltyCardTable.id], it[LoyaltyCardTable.balance], it[LoyaltyCardTable.userId])
            }

            // 3. Process expiration
            cards.forEach { (cardId, balance, cardUserId) ->
                // Reset balance
                LoyaltyCardTable.update({ LoyaltyCardTable.id eq cardId }) {
                    it[this.balance] = 0.0
                    it[lastActivityAt] = now 
                }

                // Log transaction
                TransactionsHistoryTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[userId] = cardUserId
                    it[tradingPointId] = "SYSTEM_EXPIRATION"
                    it[cashierId] = "SYSTEM"
                    it[type] = "EXPIRATION"
                    it[amount] = 0.0
                    it[pointsDelta] = -balance
                    it[visitsDelta] = 0
                    it[timestamp] = now
                }
                expiredCount++
            }
        }
        
        if (expiredCount > 0) {
            logger.info("Expiration cycle completed. Expired $expiredCount cards.")
        }
    }
}

package io.loyaltyloop.server.utils

import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.models.getDefaultTiers
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.time.LocalDateTime
import java.util.UUID

// TODO checked
fun initializeLoyaltySettings(
    pointId: UUID,
    partnerId: UUID,
    createdAt: LocalDateTime,
    request: CreateTradingPointRequest
) {
    LoyaltySettingsTable.insert {
        it[tradingPoint] = pointId
        it[partner] = partnerId
        it[programType] = request.programType

        it[this.createdAt] = createdAt
        it[this.maxBurnPercentage] = request.maxBurnPercentage
        it[awardOnMixedPayment] = request.awardOnMixedPayment
    }

    val tiersCount = LoyaltyTiersTable
        .select { LoyaltyTiersTable.partner eq partnerId }
        .count()

    if (tiersCount == 0L) {
        LoyaltyTiersTable.batchInsert(
            getDefaultTiers
        ) { def ->
            this[LoyaltyTiersTable.partner] = partnerId
            this[LoyaltyTiersTable.levelIndex] = def.index
            this[LoyaltyTiersTable.name] = def.name
            this[LoyaltyTiersTable.threshold] = def.threshold
            this[LoyaltyTiersTable.cashbackPercent] = def.cashback
        }
    }
}

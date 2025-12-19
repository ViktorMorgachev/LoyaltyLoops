package io.loyaltyloop.server.utils

import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.math.BigDecimal
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
        val baseCashback = request.baseCashback

        LoyaltyTiersTable.batchInsert(
            listOf(
                TierDef(1, "Start", BigDecimal.ZERO, BigDecimal.valueOf(baseCashback)),
                TierDef(2, "Gold", BigDecimal.valueOf(5000), BigDecimal.valueOf(baseCashback + 2.0)),
                TierDef(3, "Platinum", BigDecimal.valueOf(15000), BigDecimal.valueOf(baseCashback + 5.0))
            )
        ) { def ->
            this[LoyaltyTiersTable.partner] = partnerId
            this[LoyaltyTiersTable.levelIndex] = def.index
            this[LoyaltyTiersTable.name] = def.name
            this[LoyaltyTiersTable.threshold] = def.threshold
            this[LoyaltyTiersTable.cashbackPercent] = def.cashback
        }
    }
}

private data class TierDef(
    val index: Int,
    val name: String,
    val threshold: BigDecimal,
    val cashback: BigDecimal
)
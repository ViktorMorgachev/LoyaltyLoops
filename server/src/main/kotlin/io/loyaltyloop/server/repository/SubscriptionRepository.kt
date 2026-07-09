package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.PlatformSubscriptionsTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.models.SubscriptionWarningDto
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.ExpiringPointDto
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import java.time.LocalDateTime
import java.util.UUID

// TODO checked
class SubscriptionRepository {

    data class ExpiringSubData(
        val pointId: UUID,
        val pointName: String,
        val partnerId: UUID,
        val partnerName: String,
        val endDate: LocalDateTime
    )

    suspend fun getExpiringPointsForPartner(partnerId: String): List<ExpiringPointDto>? = dbQuery {
        val partnerUuid = partnerId.toUUID()
        val now = nowUtc()
        val warningThreshold = now.plusDays(3)

        val rows = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable)
            .slice(
                TradingPointsTable.id,
                TradingPointsTable.name,
                TradingPointsTable.partner,
                PartnersTable.businessName,
                PlatformSubscriptionsTable.endDate
            )
            .select {
                (TradingPointsTable.partner eq partnerUuid) and
                        (PlatformSubscriptionsTable.isActive eq true) and
                        (TradingPointsTable.isActive eq true) and
                        (PlatformSubscriptionsTable.endDate.isNotNull())
            }
            .map { row ->
                ExpiringSubData(
                    partnerId = partnerUuid,
                    partnerName = row[PartnersTable.businessName],
                    pointId = row[TradingPointsTable.id].value,
                    pointName = row[TradingPointsTable.name],
                    endDate = row[PlatformSubscriptionsTable.endDate]!!
                )
            }

        if (rows.isEmpty()) return@dbQuery null

        val warnings = rows
            .groupBy { it.pointId }
            .mapNotNull { (_, subs) ->

                val maxEndDate = subs.maxOf { it.endDate }
                val pointName = subs.first().pointName

                val isExpiringSoon = maxEndDate.isAfter(now) && !maxEndDate.isAfter(warningThreshold)

                if (isExpiringSoon) {
                    ExpiringPointDto(
                        pointName = pointName,
                        endDate = maxEndDate.toUtcMillis()
                    )
                } else {
                    null
                }
            }

        return@dbQuery warnings.ifEmpty { null }
    }

    suspend fun getExpiringSubscriptions(): List<SubscriptionWarningDto> = dbQuery {
        val now = nowUtc() // LocalDateTime
        val warningThreshold = now.plusDays(3)

        val rows = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable)
            .slice(
                TradingPointsTable.id,
                TradingPointsTable.name,
                TradingPointsTable.partner,
                PartnersTable.businessName,
                PlatformSubscriptionsTable.endDate
            )
            .select {
                (PlatformSubscriptionsTable.isActive eq true) and
                        (PlatformSubscriptionsTable.endDate.isNotNull())
            }
            .map { row ->
                ExpiringSubData(
                    pointId = row[TradingPointsTable.id].value,
                    pointName = row[TradingPointsTable.name],
                    partnerId = row[TradingPointsTable.partner].value,
                    partnerName = row[PartnersTable.businessName],
                    endDate = row[PlatformSubscriptionsTable.endDate]!!
                )
            }

        val warnings = rows
            .groupBy { it.pointId }
            .mapNotNull { (_, subs) ->
                val maxEndDate = subs.maxOf { it.endDate }

                val isExpiring = maxEndDate.isAfter(now) && !maxEndDate.isAfter(warningThreshold)

                if (isExpiring) {
                    val info = subs.first()
                    SubscriptionWarningDto(
                        partnerId = info.partnerId.toString(),
                        partnerName = info.partnerName,
                        pointId = info.pointId.toString(),
                        pointName = info.pointName,
                        endDate = maxEndDate.toUtcMillis()
                    )
                } else {
                    null
                }
            }

        warnings
    }
}

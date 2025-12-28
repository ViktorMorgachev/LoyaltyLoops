package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.*
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.initializeLoyaltySettings
import io.loyaltyloop.server.utils.json
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toTradingPointDto
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.shared.models.*
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

// TODO checked
class TradingPointRepository {
    suspend fun countByPartnerId(partnerId: String): Int = dbQuery {
        TradingPointsTable
            .select { TradingPointsTable.partner eq partnerId.toUUID() }
            .count()
            .toInt()
    }

    suspend fun getPointById(pointId: String): TradingPointDto = dbQuery {
        TradingPointsTable
            .selectAll()
            .where { TradingPointsTable.id eq pointId.toUUID() }
            .map { it.toTradingPointDto() }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND)
    }

    suspend fun getPointsByPartnerId(partnerId: String): List<TradingPointDto> = dbQuery {
        TradingPointsTable
            .selectAll()
            .where { TradingPointsTable.partner eq partnerId.toUUID() }
            .map { it.toTradingPointDto() }
    }

    suspend fun findTradingPointByInvite(code: String): TradingPointDto = dbQuery {
        val row = TradingPointsTable.selectAll()
            .where { TradingPointsTable.inviteCode eq code }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.INVALID_INVITE_CODE, "Invalid trading point code")

        row.toTradingPointDto()
    }

    suspend fun getPartnerIdByPointId(pointId: String): String = dbQuery {
        val pointUuid = pointId.toUUID()

        TradingPointsTable
            .slice(TradingPointsTable.partner)
            .select { TradingPointsTable.id eq pointUuid }
            .singleOrNull()
            ?.get(TradingPointsTable.partner)?.value?.toString()
            ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND, "Trading point not found")
    }
    suspend fun getSettingsByPointId(pointId: String): LoyaltySettingsDto = dbQuery {
        val pointUuid = pointId.toUUID()

        val row = LoyaltySettingsTable
            .join(TradingPointsTable, JoinType.INNER, onColumn = LoyaltySettingsTable.tradingPoint, otherColumn = TradingPointsTable.id)
            .join(PartnersTable, JoinType.INNER, onColumn = TradingPointsTable.partner, otherColumn = PartnersTable.id)
            .select { LoyaltySettingsTable.tradingPoint eq pointUuid }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.LOYALTY_SETTING_NOT_FOUND, "Loyalty Settings not found for point $pointId")

        val partnerId = row[PartnersTable.id].value

        val tiers = LoyaltyTiersTable
            .select { LoyaltyTiersTable.partner eq partnerId }
            .orderBy(LoyaltyTiersTable.threshold)
            .map { tierRow ->
                LoyaltyTierDto(
                    levelIndex = tierRow[LoyaltyTiersTable.levelIndex],
                    loyaltyTier = LoyaltyTierDto.LoyaltyTier(
                        level = indexToLoyaltyLevel(tierRow[LoyaltyTiersTable.levelIndex]),
                        descr = tierRow[LoyaltyTiersTable.name]
                    ),
                    threshold = tierRow[LoyaltyTiersTable.threshold].toDouble(),
                    cashbackPercent = tierRow[LoyaltyTiersTable.cashbackPercent].toDouble()
                )
            }

        LoyaltySettingsDto(
            settingsId = row[LoyaltySettingsTable.id].value.toString(),
            tradingPointId = pointId,
            programType = row[LoyaltySettingsTable.programType],
            currency = row[TradingPointsTable.currency],
            maxBurnPercentage = row[LoyaltySettingsTable.maxBurnPercentage],
            awardOnMixedPayment = row[LoyaltySettingsTable.awardOnMixedPayment],
            visitsReward = row[LoyaltySettingsTable.visitsReward],
            visitsTarget = row[PartnersTable.defaultVisitsTarget],
            burnBonusesAfterDays = row[PartnersTable.burnBonusesDays],
            downgradeTierAfterDays = row[PartnersTable.downgradeTierDays],
            tiers = tiers
        )
    }

    suspend fun createTradingPoint(partnerId: String, request: CreateTradingPointRequest): String = dbQuery {
        val partnerUuid = partnerId.toUUID()
        val createdAt = nowUtc()

        val newPointId = TradingPointsTable.insertAndGetId {
            it[partner] = partnerUuid
            it[name] = request.name
            it[type] = request.type
            it[address] = request.address
            it[latitude] = request.latitude
            it[longitude] = request.longitude
            it[currency] = request.currency.name
            it[timezone] = request.timezone
            it[workingHoursJson] = request.schedule?.let { s ->
                json.encodeToString(s)
            }
            it[isTemporarilyPaused] = request.temporarilyPaused
            it[contactPhone] = request.contactPhone
            it[contactLink] = request.contactLink
            it[additionalInfo] = request.additionalInfo
            it[this.createdAt] = createdAt
            it[isActive] = false
            it[rating] = 0.0
            it[ratingCount] = 0
        }


        initializeLoyaltySettings(
            pointId = newPointId.value,
            partnerId = partnerUuid,
            request = request,
            createdAt = createdAt
        )

        newPointId.value.toString()
    }

    suspend fun updateTradingPoint(pointId: String, request: UpdateTradingPointRequest) = dbQuery {
        val pointUuid = pointId.toUUID()
        val updateNow = nowUtc()

        val currentData = TradingPointsTable.slice(TradingPointsTable.currency)
            .select { TradingPointsTable.id eq pointUuid }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND)

        val currentCurrency = currentData[TradingPointsTable.currency]

        if (request.currency != currentCurrency) {
            val hasTransactions = TransactionsHistoryTable
                .select { TransactionsHistoryTable.tradingPoint eq pointUuid }
                .count() > 0

            if (hasTransactions) {
                throw LoyaltyException(
                    AppErrorCode.FORBIDDEN,
                    "Cannot change currency. This point already has transaction history."
                )
            }
        }

        TradingPointsTable.update({ TradingPointsTable.id eq pointUuid }) {
            it[name] = request.name
            it[type] = request.type
            it[address] = request.address
            it[latitude] = request.latitude
            it[longitude] = request.longitude
            it[currency] = request.currency
            it[workingHoursJson] = request.schedule?.let { s ->
                json.encodeToString(s)
            }
            it[updatedAt] = updateNow
            it[isTemporarilyPaused] = request.temporarilyPaused
            it[contactPhone] = request.contactPhone
            it[contactLink] = request.contactLink
            it[additionalInfo] = request.additionalInfo
            it[timezone] = request.timezone
        }

        LoyaltySettingsTable.update({ LoyaltySettingsTable.tradingPoint eq pointUuid }) {
            it[programType] = request.settings.programType
            it[updatedAt] = updateNow
            it[maxBurnPercentage] = request.settings.maxBurnPercentage
            it[awardOnMixedPayment] = request.settings.awardOnMixedPayment
        }
    }
    suspend fun deleteTradingPoint(pointId: String) = dbQuery {
        val pointUuid = pointId.toUUID()

        val deletedCount = TradingPointsTable.deleteWhere {
            TradingPointsTable.id eq pointUuid
        }
        if (deletedCount == 0) {
            throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND, "Trading point not found or already deleted")
        }
    }
    suspend fun updatePointStatus(pointId: String, isActive: Boolean) = dbQuery {
        val pointUuid = pointId.toUUID()

        val updatedCount = TradingPointsTable.update({ TradingPointsTable.id eq pointUuid }) {
            it[this.isActive] = isActive
            it[this.updatedAt] = nowUtc()
        }
        if (updatedCount == 0) {
            throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND)
        }
    }

    suspend fun getPointDetails(pointId: String, partnerId: String): TradingPointDetailsDto = dbQuery {
        val pointUuid = pointId.toUUID()
        val partnerUuid = partnerId.toUUID()

        val row = TradingPointsTable
            .join(PartnersTable, JoinType.INNER, onColumn = TradingPointsTable.partner, otherColumn = PartnersTable.id)
            .join(LoyaltySettingsTable, JoinType.INNER, onColumn = TradingPointsTable.id, otherColumn = LoyaltySettingsTable.tradingPoint)
            .select { TradingPointsTable.id eq pointUuid }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND)

        if (row[TradingPointsTable.partner].value != partnerUuid) {
            throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied to trading point")
        }

        val pointDto = row.toTradingPointDto()

        val tiers = LoyaltyTiersTable
            .select { LoyaltyTiersTable.partner eq partnerUuid }
            .orderBy(LoyaltyTiersTable.threshold)
            .map { tierRow ->
                LoyaltyTierDto(
                    levelIndex = tierRow[LoyaltyTiersTable.levelIndex],
                    loyaltyTier = LoyaltyTierDto.LoyaltyTier(
                        level = indexToLoyaltyLevel(tierRow[LoyaltyTiersTable.levelIndex]),
                        descr = tierRow[LoyaltyTiersTable.name]
                    ),
                    threshold = tierRow[LoyaltyTiersTable.threshold].toDouble(),
                    cashbackPercent = tierRow[LoyaltyTiersTable.cashbackPercent].toDouble()
                )
            }

        val settingsDto = LoyaltySettingsDto(
            settingsId = row[LoyaltySettingsTable.id].value.toString(),
            tradingPointId = pointId,
            programType = row[LoyaltySettingsTable.programType],
            currency = row[TradingPointsTable.currency],
            maxBurnPercentage = row[LoyaltySettingsTable.maxBurnPercentage],
            awardOnMixedPayment = row[LoyaltySettingsTable.awardOnMixedPayment],
            visitsReward = row[LoyaltySettingsTable.visitsReward],
            visitsTarget = row[PartnersTable.defaultVisitsTarget],
            burnBonusesAfterDays = row[PartnersTable.burnBonusesDays],
            downgradeTierAfterDays = row[PartnersTable.downgradeTierDays],
            tiers = tiers
        )

        TradingPointDetailsDto(
            point = pointDto,
            settings = settingsDto,
            baseCurrency = row[PartnersTable.baseCurrency]
        )
    }

}

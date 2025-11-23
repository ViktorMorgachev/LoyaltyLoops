package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.CashiersTable
import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltySettingsDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.PartnerDto
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType
import io.loyaltyloop.shared.models.UpdatePartnerRequest
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class PartnerRepository {

    suspend fun createPartner(ownerId: String, request: CreatePartnerRequest): String = dbQuery {
        val alreadyExists = PartnersTable.select { PartnersTable.ownerId eq ownerId }.count() > 0
        if (alreadyExists) {
            throw IllegalStateException("User already owns a business.")
        }

        val newPartnerId = UUID.randomUUID().toString()

        PartnersTable.insert {
            it[id] = newPartnerId
            it[this.ownerId] = ownerId
            it[businessName] = request.businessName
            it[countryCode] = request.countryCode
            it[status] = PartnerStatus.PENDING
            it[adminPinHash] = null
        }

        newPartnerId
    }

    // Получить партнера по ID владельца


    suspend fun getPartnerByOwnerId(ownerId: String): PartnerEntity? = dbQuery {
        PartnersTable.select { PartnersTable.ownerId eq ownerId }
            .map { mapPartnerEntity(it) }
            .singleOrNull()
    }

    // Обновить настройки партнера
    suspend fun updatePartner(ownerId: String, request: UpdatePartnerRequest) = dbQuery {
        PartnersTable.update({ PartnersTable.ownerId eq ownerId }) {
            it[businessName] = request.businessName
            it[color] = request.color
            it[logoUrl] = request.logoUrl
        }
    }



    suspend fun getAllPartners(): List<PartnerEntity> = dbQuery {
        PartnersTable.selectAll()
            .map {
                PartnerEntity(
                    id = it[PartnersTable.id],
                    name = it[PartnersTable.businessName],
                    hasPin = !it[PartnersTable.adminPinHash].isNullOrBlank(),
                    status = it[PartnersTable.status],
                    logoUrl = it[PartnersTable.logoUrl],
                    color = it[PartnersTable.color]
                )
            }
    }

    suspend fun getPartnerById(id: String): PartnerEntity? = dbQuery {
        PartnersTable.select { PartnersTable.id eq id }
            .map {
                PartnerEntity(
                    id = it[PartnersTable.id],
                    name = it[PartnersTable.businessName],
                    hasPin = !it[PartnersTable.adminPinHash].isNullOrBlank(),
                    status = it[PartnersTable.status],
                    logoUrl = it[PartnersTable.logoUrl],
                    color = it[PartnersTable.color]
                )
            }
            .singleOrNull()
    }


    // 1. Найти бизнесы, где я Владелец
    suspend fun getPartnersByOwner(userId: String): List<PartnerEntity> = dbQuery {
        PartnersTable.selectAll().where { PartnersTable.ownerId eq userId }
            .map {
                PartnerEntity(
                    id = it[PartnersTable.id],
                    name = it[PartnersTable.businessName],
                    hasPin = !it[PartnersTable.adminPinHash].isNullOrBlank(),
                    status = it[PartnersTable.status],
                    logoUrl = it[PartnersTable.logoUrl],
                    color = it[PartnersTable.color]
                )
            }
    }

    suspend fun updateStatus(partnerId: String, newStatus: PartnerStatus) = dbQuery {
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[status] = newStatus
        }
    }

    suspend fun getPointsByPartnerId(partnerId: String): List<TradingPointDto> = dbQuery {
        TradingPointsTable.select { TradingPointsTable.partnerId eq partnerId }
            .map {
                TradingPointDto(
                    id = it[TradingPointsTable.id],
                    name = it[TradingPointsTable.name],
                    address = it[TradingPointsTable.address],
                    type = it[TradingPointsTable.type],
                    latitude = it[TradingPointsTable.latitude],
                    longitude = it[TradingPointsTable.longitude],
                    active = it[TradingPointsTable.isActive],
                    inviteCode = it[TradingPointsTable.inviteCode]
                )
            }
    }

    // --- СОЗДАНИЕ ТОЧКИ (Исправлено) ---
    suspend fun createTradingPoint(
        partnerId: String,
        latitude: Double? = null,
        longitude: Double? = null,
        request: CreateTradingPointRequest // Используем данные из запроса для настроек
    ) = dbQuery {
        val pointID = java.util.UUID.randomUUID().toString()
        TradingPointsTable.insert {
            it[this.id] = pointID
            it[this.partnerId] = partnerId
            it[this.name] = request.name
            it[this.type] = request.type
            it[this.address] = request.address
            it[this.latitude] = latitude
            it[this.longitude] = longitude
            it[this.inviteCode] = (100000..999999).random().toString()

            // ИСПРАВЛЕНО: По умолчанию точка НЕ АКТИВНА (ждет оплаты)
            it[this.isActive] = false
        }

        // Создаем настройки
        val settingsId = UUID.randomUUID().toString()

        LoyaltySettingsTable.insert {
            it[id] = settingsId
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = pointID
            it[programType] = request.programType.name
            it[visitsTarget] = request.visitsTarget ?: 6
            it[visitsResetValue] = 0
        }

        // Создаем уровни (Если TIERED)
        if (request.programType == LoyaltyProgramType.TIERED_LTV) {
            // Базовый процент берем из запроса (например 5% = 0.05) или 3% по дефолту
            val base = request.baseCashback ?: 0.03

            // Автоматически создаем лесенку уровней:
            // 1. Start: 0 сом -> База (5%)
            // 2. Silver: 10000 сом -> База + 2% (7%)
            // 3. Gold: 50000 сом -> База + 5% (10%)
            val levels = listOf(
                Triple(1, "Start", base),
                Triple(2, "Silver", base + 0.02),
                Triple(3, "Gold", base + 0.05)
            )

            levels.forEach { (idx, name, percent) ->
                LoyaltyTiersTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[this.settingsId] = settingsId
                    it[levelIndex] = idx
                    it[this.name] = name
                    it[threshold] = if (idx == 1) 0.0 else (idx * 10000.0) // Примерные пороги
                    it[cashbackPercent] = percent
                }
            }
        }

        pointID
    }

    private fun mapPartnerEntity(row: org.jetbrains.exposed.sql.ResultRow) = PartnerEntity(
        id = row[PartnersTable.id],
        name = row[PartnersTable.businessName],
        hasPin = !row[PartnersTable.adminPinHash].isNullOrBlank(),
        status = row[PartnersTable.status],
        logoUrl = row[PartnersTable.logoUrl],
        color = row[PartnersTable.color]
    )

    suspend fun getSettingsByPointId(pointId: String): LoyaltySettingsDto? = dbQuery {
        val settingsRow = LoyaltySettingsTable.select {
            LoyaltySettingsTable.tradingPointId eq pointId
        }.singleOrNull() ?: return@dbQuery null

        val settingsId = settingsRow[LoyaltySettingsTable.id]

        val tiers = LoyaltyTiersTable.select {
            LoyaltyTiersTable.settingsId eq settingsId
        }.map {
            LoyaltyTierDto(
                levelIndex = it[LoyaltyTiersTable.levelIndex],
                name = it[LoyaltyTiersTable.name],
                threshold = it[LoyaltyTiersTable.threshold],
                cashbackPercent = it[LoyaltyTiersTable.cashbackPercent]
            )
        }

        LoyaltySettingsDto(
            settingsId = settingsId,
            tradingPointId = pointId,
            programType = LoyaltyProgramType.valueOf(settingsRow[LoyaltySettingsTable.programType]),
            tiers = tiers,
            visitsTarget = settingsRow[LoyaltySettingsTable.visitsTarget],
            visitsReward = settingsRow[LoyaltySettingsTable.visitsReward]
        )
    }

    // --- МЕТОДЫ ДЛЯ ИНВАЙТОВ ---
    suspend fun findTradingPointByInvite(code: String): TradingPointDto? = dbQuery {
        TradingPointsTable.select { TradingPointsTable.inviteCode eq code }
            .map {
                TradingPointDto(
                    id = it[TradingPointsTable.id],
                    name = it[TradingPointsTable.name],
                    active = it[TradingPointsTable.isActive],
                    type = it[TradingPointsTable.type],
                    address = it[TradingPointsTable.address],
                    latitude = it[TradingPointsTable.latitude],
                    longitude = it[TradingPointsTable.longitude],
                    inviteCode = it[TradingPointsTable.inviteCode]
                )
            }
            .singleOrNull()
    }

    suspend fun isUserCashierAtPoint(userId: String, pointId: String): Boolean = dbQuery {
        !CashiersTable.select {
            (CashiersTable.userId eq userId) and (CashiersTable.tradingPointId eq pointId)
        }.empty()
    }

    suspend fun addCashier(userId: String, pointId: String, partnerId: String) = dbQuery {
        CashiersTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.userId] = userId
            it[this.tradingPointId] = pointId
            it[this.partnerId] = partnerId
            it[canRefund] = false
            it[isActive] = true
        }
    }

    suspend fun getPartnerIdByPoint(pointId: String): String? = dbQuery {
        TradingPointsTable.select { TradingPointsTable.id eq pointId }
            .map { it[TradingPointsTable.partnerId] }
            .singleOrNull()
    }
}

@Serializable
data class PartnerEntity(
    val id: String,
    val name: String,
    val hasPin: Boolean,
    val status: PartnerStatus,
    val logoUrl: String?,
    val color: String
)

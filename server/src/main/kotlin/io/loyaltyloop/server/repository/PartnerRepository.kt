package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.CashiersTable
import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TradingPointsTable.isActive
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltySettingsDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class PartnerRepository {

    // Создание самого бизнеса (просто запись в partners)
    suspend fun createPartner(id: String, ownerId: String, name: String) = dbQuery {
        PartnersTable.insert {
            it[this.id] = id
            it[this.ownerId] = ownerId
            it[this.businessName] = name
            it[this.countryCode] = "KG" // Хардкод для теста
            it[this.adminPinHash] = null
        }
    }

    suspend fun getPartnerById(id: String): PartnerEntity? = dbQuery {
        PartnersTable.selectAll().where { PartnersTable.id eq id }
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

    // 1. Найти точку по инвайту
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

    // 2. Проверка: уже работает здесь?
    suspend fun isUserCashierAtPoint(userId: String, pointId: String): Boolean = dbQuery {
        !CashiersTable.selectAll()
            .where { (CashiersTable.userId eq userId) and (CashiersTable.tradingPointId eq pointId) }
            .empty()
    }

    // 3. Добавить кассира
    suspend fun addCashier(userId: String, pointId: String, partnerId: String) = dbQuery {
        CashiersTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.userId] = userId
            it[this.tradingPointId] = pointId
            it[this.partnerId] = partnerId
            it[this.isActive] = true
            it[this.canRefund] = false
        }
    }

    // 1. Найти ID точки по инвайту


    // 4. Получить PartnerID по PointID
    suspend fun getPartnerIdByPoint(pointId: String): String? = dbQuery {
        TradingPointsTable.select { TradingPointsTable.id eq pointId }
            .map { it[TradingPointsTable.partnerId] }
            .singleOrNull()
    }

    // --- МАГИЯ ЗДЕСЬ ---
    suspend fun createTradingPoint(
        partnerId: String,
        request: CreateTradingPointRequest // <-- Передаем весь объект запроса для удобства
    ) = dbQuery {

        val pointId = java.util.UUID.randomUUID().toString()

        // 1. Точка
        TradingPointsTable.insert {
            it[this.id] = pointId
            it[this.partnerId] = partnerId
            it[this.name] = request.name
            it[this.type] = request.type
            it[this.address] = request.address
            it[this.inviteCode] = (100000..999999).random().toString()
            it[this.isActive] = true
        }

        // 2. Настройки
        val settingsId = java.util.UUID.randomUUID().toString()

        LoyaltySettingsTable.insert {
            it[id] = settingsId
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = pointId

            // Берем из запроса
            it[programType] = request.programType.name
            it[visitsTarget] = request.visitsTarget ?: 6 // Дефолт 6, если не указали
            it[visitsResetValue] = 0
        }

        // 3. Уровни (Зависят от типа)
        if (request.programType == LoyaltyProgramType.TIERED_LTV) {
            // Если TIERED - создаем 3 уровня
            // Базовый процент берем из запроса или 3%
            val base = request.baseCashback ?: 0.03

            val levels = listOf(
                Triple(1, "Start", base),
                Triple(2, "Silver", base + 0.02), // +2%
                Triple(3, "Gold", base + 0.05)    // +5%
            )

            // ... цикл insert LoyaltyTiersTable (тот же код, что был) ...
            levels.forEach { (idx, name, percent) ->
                LoyaltyTiersTable.insert {
                    it[id] = java.util.UUID.randomUUID().toString()
                    it[this.settingsId] = settingsId
                    it[levelIndex] = idx
                    it[this.name] = name
                    it[threshold] = if (idx == 1) 0.0 else (idx * 10000.0)
                    it[cashbackPercent] = percent
                }
            }
        }

        pointId
    }

    suspend fun createPartner(ownerId: String, request: CreatePartnerRequest): String = dbQuery {
        val newId = java.util.UUID.randomUUID().toString()

        PartnersTable.insert {
            it[id] = newId
            it[this.ownerId] = ownerId
            it[businessName] = request.businessName
            it[countryCode] = request.countryCode
            it[status] = PartnerStatus.PENDING // <-- По умолчанию на проверке
            // Пин код пока null, его зададут позже
        }

        // Сразу создаем дефолтные настройки лояльности!
        // (Логику createTradingPoint и Settings можно вызывать тут или создавать дефолтную точку "Main" сразу)

        newId
    }

    // Новый метод для чтения (нужен для теста и для API)
    suspend fun getTradingPointById(id: String): TradingPointDto? = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.id eq id }
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
            .singleOrNull()
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
                    // Добавим инвайт код, так как это админка владельца, ему нужно его знать
                    // (В DTO нужно добавить поле inviteCode, если его нет, см. шаг ниже)
                )
            }
    }

    suspend fun getSettingsByPointId(pointId: String): LoyaltySettingsDto? = dbQuery {
        // 1. Ищем настройки
        val settingsRow = LoyaltySettingsTable.select {
            LoyaltySettingsTable.tradingPointId eq pointId
        }.singleOrNull() ?: return@dbQuery null

        val settingsId = settingsRow[LoyaltySettingsTable.id]

        // 2. Ищем уровни для этих настроек
        val tiers = LoyaltyTiersTable.selectAll()
            .where { LoyaltyTiersTable.settingsId eq settingsId }
            .map {
                LoyaltyTierDto(
                    levelIndex = it[LoyaltyTiersTable.levelIndex],
                    name = it[LoyaltyTiersTable.name],
                    threshold = it[LoyaltyTiersTable.threshold],
                    cashbackPercent = it[LoyaltyTiersTable.cashbackPercent]
                )
            }

        // 3. Собираем DTO
        LoyaltySettingsDto(
            settingsId = settingsId, // <-- Не забудь добавить id в модель shared/LoyaltySettingsDto, если его там нет
            tradingPointId = pointId,
            programType = LoyaltyProgramType.valueOf(settingsRow[LoyaltySettingsTable.programType]),
            tiers = tiers,
            visitsTarget = settingsRow[LoyaltySettingsTable.visitsTarget],
            visitsReward = settingsRow[LoyaltySettingsTable.visitsReward]
        )
    }
}
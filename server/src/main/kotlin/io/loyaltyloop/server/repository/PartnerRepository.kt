package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.CashiersTable
import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TradingPointsTable.isActive
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltySettingsDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
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
            it[isActive] = true
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
        pointId: String,
        name: String,
        type: TradingPointType = TradingPointType.OTHER, // Дефолт
        address: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) = dbQuery {
        // 1. Создаем саму точку
        TradingPointsTable.insert {
            it[this.id] = pointId
            it[this.partnerId] = partnerId
            it[this.name] = name
            it[this.inviteCode] = (100000..999999).random().toString()
            it[this.isActive] = false
            it[this.address] = address
            it[this.type] = type
            it[this.latitude] = latitude
            it[this.longitude] = longitude// Для теста активна
        }

        // 2. Создаем настройки для этой точки (TIERED по умолчанию)
        val settingsId = UUID.randomUUID().toString()

        LoyaltySettingsTable.insert {
            it[id] = settingsId
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = pointId // Привязываем к точке!
            it[programType] = LoyaltyProgramType.TIERED_LTV.name
        }

        // 3. Создаем 3 уровня по умолчанию
        // Уровень 1: Start (3%)
        LoyaltyTiersTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.settingsId] = settingsId
            it[levelIndex] = 1
            it[this.name] = "Start"
            it[threshold] = 0.0
            it[cashbackPercent] = 0.03
        }

        // Уровень 2: Middle (5%)
        LoyaltyTiersTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.settingsId] = settingsId
            it[levelIndex] = 2
            it[this.name] = "Middle"
            it[threshold] = 10000.0
            it[cashbackPercent] = 0.05
        }

        // Уровень 3: Top (10%)
        LoyaltyTiersTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.settingsId] = settingsId
            it[levelIndex] = 3
            it[this.name] = "Top"
            it[threshold] = 50000.0
            it[cashbackPercent] = 0.10
        }
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
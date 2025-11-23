package io.loyaltyloop.server.repository


import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnerCashiersTable
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.LoyaltySettingsTable
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.database.tables.PartnerManagersTable
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.Employer
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltySettingsDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointType
import io.loyaltyloop.shared.models.UpdatePartnerRequest
import io.loyaltyloop.shared.models.UpdateTradingPointRequest
import io.loyaltyloop.shared.models.PartnerStatsDto
import org.jetbrains.exposed.sql.JoinType
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.count
import java.util.UUID
import kotlin.jvm.Throws

class PartnerRepository {

    suspend fun createPartner(userID: String, request: CreatePartnerRequest): String = dbQuery {
        val alreadyExists = PartnersTable.selectAll().where { PartnersTable.ownerId eq userID }.count() > 0
        if (alreadyExists) {
            throw LoyaltyException(AppErrorCode.BUSINESS_ALREADY_EXISTS, "User already owns a business.")
        }

        val newPartnerId = UUID.randomUUID().toString()

        PartnersTable.insert {
            it[id] = newPartnerId
            it[ownerId] = userID
            it[businessName] = request.businessName
            it[countryCode] = request.countryCode
            it[status] = PartnerStatus.PENDING
            it[adminPinHash] = null
        }

        newPartnerId
    }

    // Получить партнера по ID владельца
    @Throws(LoyaltyException::class)
    suspend fun getPartnerByUserId(userID: String): PartnerEntity = dbQuery {
        PartnersTable.selectAll().where { PartnersTable.ownerId eq userID }
            .map { mapPartnerEntity(it) }
            .singleOrNull() ?: throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND,"getPartnerByOwnerId: Partner not found for this user")
    }

    suspend fun getPartnerById(partnerID: String): PartnerEntity = dbQuery {
        PartnersTable.selectAll().where { PartnersTable.id eq partnerID }
            .map { mapPartnerEntity(it) }
            .singleOrNull() ?: throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND,"getPartnerById: Partner not found in database")
    }

    // Обновить настройки партнера
    suspend fun updatePartner(ownerId: String, request: UpdatePartnerRequest) = dbQuery {
        PartnersTable.update({ PartnersTable.ownerId eq ownerId }) {
            it[businessName] = request.businessName
            it[color] = request.color
            it[logoUrl] = request.logoUrl
            it[burnBonusesDays] = request.burnBonusesDays
            it[downgradeTierDays] = request.downgradeTierDays
        }
    }

    suspend fun getAllPartners(): List<PartnerEntity> = dbQuery {
        PartnersTable.join(UsersTable, JoinType.INNER, PartnersTable.ownerId, UsersTable.id)
            .selectAll()
            .map { mapPartnerEntity(it).copy(ownerPhone = it[UsersTable.phoneNumber]) }
    }




    // 1. Найти бизнесы, где я Владелец
    suspend fun getPartnersByOwner(userId: String): List<PartnerEntity> = dbQuery {
        PartnersTable.selectAll().where { PartnersTable.ownerId eq userId }
            .map { mapPartnerEntity(it) }
    }

    suspend fun updateStatus(partnerId: String, newStatus: PartnerStatus) = dbQuery {
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[status] = newStatus
        }
    }

    suspend fun getPointsByPartnerId(partnerId: String): List<TradingPointDto> = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.partnerId eq partnerId }
            .map { mapTradingPointEntity(it) }
    }

    @Throws(LoyaltyException::class)
    suspend fun getPointById(pointId: String): TradingPointDto = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.id eq pointId }
            .map { mapTradingPointEntity(it) }
            .singleOrNull() ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND)
    }

    // --- СОЗДАНИЕ ТОЧКИ (Исправлено) ---
    suspend fun createTradingPoint(
        partnerId: String,
        request: CreateTradingPointRequest // Используем данные из запроса для настроек
    ) = dbQuery {
        val pointID = UUID.randomUUID().toString()
        TradingPointsTable.insert {
            it[this.id] = pointID
            it[this.partnerId] = partnerId
            it[this.name] = request.name
            it[this.type] = request.type
            it[this.address] = request.address
            it[this.latitude] = latitude
            it[this.longitude] = longitude
            it[this.currency] = request.currency
            it[this.inviteCode] = generateUniqueInviteCode()

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
            it[visitsTarget] = request.visitsTarget
            it[maxBurnPercentage] = 100
        }

        // Создаем уровни (Если TIERED или HYBRID)
        if (request.programType == LoyaltyProgramType.TIERED_LTV || request.programType == LoyaltyProgramType.HYBRID) {
            // Базовый процент берем из запроса (например 5% = 0.05) или 3% по дефолту
            val base = request.baseCashback

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

    private fun mapPartnerEntity(row: ResultRow) = PartnerEntity(
        id = row[PartnersTable.id],
        ownerId = row[PartnersTable.ownerId],
        name = row[PartnersTable.businessName],
        countryCode = row[PartnersTable.countryCode],
        hasPin = !row[PartnersTable.adminPinHash].isNullOrBlank(),
        status = row[PartnersTable.status],
        logoUrl = row[PartnersTable.logoUrl],
        color = row[PartnersTable.color],
        burnBonusesDays = row[PartnersTable.burnBonusesDays],
        downgradeTierDays = row[PartnersTable.downgradeTierDays],
        ownerPhone = null
    )

    private fun mapTradingPointEntity(row: ResultRow) = TradingPointDto(
        id = row[TradingPointsTable.id],
        name = row[TradingPointsTable.name],
        address = row[TradingPointsTable.address],
        type = row[TradingPointsTable.type],
        latitude = row[TradingPointsTable.latitude],
        longitude = row[TradingPointsTable.longitude],
        active = row[TradingPointsTable.isActive],
        inviteCode = row[TradingPointsTable.inviteCode],
        currency = row[TradingPointsTable.currency]
    )

    @Throws(LoyaltyException::class)
    suspend fun getSettingsByPointId(pointId: String): LoyaltySettingsDto = dbQuery {
        val settingsRow = LoyaltySettingsTable.selectAll().where {
            LoyaltySettingsTable.tradingPointId eq pointId
        }.singleOrNull() ?:  throw LoyaltyException(AppErrorCode.LOYALTY_SETTING_NOT_FOUND, "Loyalty Settings not found by point ${pointId}")

        val settingsId = settingsRow[LoyaltySettingsTable.id]

        val tiers = LoyaltyTiersTable.selectAll().where {
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
            visitsReward = settingsRow[LoyaltySettingsTable.visitsReward],
            burnBonusesAfterDays = settingsRow[LoyaltySettingsTable.burnBonusesDays],
            downgradeTierAfterDays = settingsRow[LoyaltySettingsTable.downgradeTierDays],
            maxBurnPercentage = settingsRow[LoyaltySettingsTable.maxBurnPercentage]
        )
    }

    // --- МЕТОДЫ ДЛЯ ИНВАЙТОВ ---
    @Throws(LoyaltyException::class)
    suspend fun findTradingPointByInvite(code: String): TradingPointDto = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.inviteCode eq code }
            .map { mapTradingPointEntity(it) }
            .singleOrNull() ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND, "Point not found by invite")
    }

    suspend fun isUserCashierAtPoint(userId: String, pointId: String): Boolean = dbQuery {
        !PartnerCashiersTable.selectAll().where {
            (PartnerCashiersTable.userId eq userId) and (PartnerCashiersTable.tradingPointId eq pointId)
        }.empty()
    }

    suspend fun addCashier(userId: String, pointId: String, partnerId: String) = dbQuery {
        PartnerCashiersTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.userId] = userId
            it[this.tradingPointId] = pointId
            it[this.partnerId] = partnerId
            it[canRefund] = false
            it[isActive] = true
        }
    }

    suspend fun getPartnerIdByPoint(pointId: String): String = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.id eq pointId }
            .map { it[TradingPointsTable.partnerId] }
            .singleOrNull() ?: throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Point has no partner")
    }

    suspend fun getPartnerIdByCashierId(cashierId: String): String? = dbQuery {
        PartnerCashiersTable.selectAll().where { PartnerCashiersTable.id eq cashierId }
            .map { it[PartnerCashiersTable.partnerId] }
            .singleOrNull()
    }

    suspend fun verifyPartnerPin(partnerId: String, pinInput: String): Boolean = dbQuery {
        val row = PartnersTable.selectAll().where { PartnersTable.id eq partnerId }.singleOrNull() ?: return@dbQuery false
        val hash = row[PartnersTable.adminPinHash]

        if (hash == null) {
            val ownerId = row[PartnersTable.ownerId]
            val ownerPhone = UsersTable.selectAll().where { UsersTable.id eq ownerId }
                .map { it[UsersTable.phoneNumber] }
                .singleOrNull() ?: return@dbQuery false

            val defaultPin = ownerPhone.takeLast(4)
            return@dbQuery pinInput == defaultPin
        }

        return@dbQuery hash == pinInput
    }

    // --- ADMIN FEATURES ---

    suspend fun updatePointStatus(pointId: String, isActive: Boolean) = dbQuery {
        TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
            it[this.isActive] = isActive
        }
    }

    suspend fun getPartnerStats(partnerId: String): PartnerStatsDto {
        return dbQuery {
            val pointsCount = TradingPointsTable.selectAll().where { TradingPointsTable.partnerId eq partnerId }.count()
            val cardsCount = LoyaltyCardTable.selectAll().where { LoyaltyCardTable.partnerId eq partnerId }.count()

            // Считаем транзакции: JOIN History -> Points (где Points.partnerId = ...)
            val transactionsCount = TransactionsHistoryTable.join(
                TradingPointsTable,
                JoinType.INNER,
                onColumn = TransactionsHistoryTable.tradingPointId,
                otherColumn = TradingPointsTable.id
            )
            .selectAll().where { TradingPointsTable.partnerId eq partnerId }
            .count()

            PartnerStatsDto(
                partnerId = partnerId,
                pointsCount = pointsCount.toInt(),
                cardsCount = cardsCount.toInt(),
                transactionsCount = transactionsCount.toInt()
            )
        }
    }

    // === NEW: Point Management (DELETE, UPDATE, CASHIERS) ===

    suspend fun deleteTradingPoint(pointId: String) = dbQuery {
        TradingPointsTable.deleteWhere { TradingPointsTable.id eq pointId }
    }

    suspend fun updateTradingPoint(pointId: String, request: UpdateTradingPointRequest) = dbQuery {
        // 1. Update Point Details
        TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
            it[name] = request.name
            it[type] = TradingPointType.valueOf(request.type)
            if (request.address != null) it[address] = request.address
            if (request.latitude != null) it[latitude] = request.latitude
            if (request.longitude != null) it[longitude] = request.longitude
            it[currency] = request.currency
        }

        val settingsReq = request.settings

        // 2. Find Settings ID
        val settingsId = LoyaltySettingsTable
            .selectAll().where { LoyaltySettingsTable.tradingPointId eq pointId }
            .map { it[LoyaltySettingsTable.id] }
            .single()

        // 3. Update Settings
        LoyaltySettingsTable.update({ LoyaltySettingsTable.id eq settingsId }) {
            it[programType] = settingsReq.programType.name
            it[visitsTarget] = settingsReq.visitsTarget
            it[maxBurnPercentage] = settingsReq.maxBurnPercentage
        }

        // 4. Re-create Tiers (Simple approach: delete all, insert new)
        LoyaltyTiersTable.deleteWhere { LoyaltyTiersTable.settingsId eq settingsId }

        if (settingsReq.programType == LoyaltyProgramType.TIERED_LTV || settingsReq.programType == LoyaltyProgramType.HYBRID) {
            settingsReq.tiers.forEach { tier ->
                LoyaltyTiersTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[this.settingsId] = settingsId
                    it[levelIndex] = tier.levelIndex
                    it[name] = tier.name
                    it[threshold] = tier.threshold
                    it[cashbackPercent] = tier.cashbackPercent
                }
            }
        }
    }

    suspend fun getCashiersByPoint(pointId: String): List<Employer> = dbQuery {
        PartnerCashiersTable.innerJoin(UsersTable)
            .selectAll().where { PartnerCashiersTable.tradingPointId eq pointId }
            .map {
                Employer(
                    id = it[PartnerCashiersTable.id],
                    userId = it[UsersTable.id],
                    name = "${it[UsersTable.firstName] ?: ""} ${it[UsersTable.lastName] ?: ""}".trim(),
                    phone = it[UsersTable.phoneNumber],
                    active = it[PartnerCashiersTable.isActive],
                    tradingPointId = it[PartnerCashiersTable.tradingPointId] // Assuming provided pointId matches
                )
            }
    }

    suspend fun getAllCashiers(partnerId: String): List<Employer> = dbQuery {
        val cashiers = PartnerCashiersTable
            .innerJoin(UsersTable)
            .innerJoin(TradingPointsTable)
            .selectAll().where { TradingPointsTable.partnerId eq partnerId }
            .toList()

        if (cashiers.isEmpty()) return@dbQuery emptyList()

        val cashierIds = cashiers.map { it[PartnerCashiersTable.id] }

        // Calculate Stats
        val stats = TransactionsHistoryTable
            .slice(TransactionsHistoryTable.cashierId, TransactionsHistoryTable.amount.sum(), TransactionsHistoryTable.id.count())
            .select { TransactionsHistoryTable.cashierId inList cashierIds }
            .groupBy(TransactionsHistoryTable.cashierId)
            .associate {
                val cId = it[TransactionsHistoryTable.cashierId]
                val rev = it[TransactionsHistoryTable.amount.sum()] ?: 0.0
                val cnt = it[TransactionsHistoryTable.id.count()]
                cId to (rev to cnt)
            }

        cashiers.map {
            val cId = it[PartnerCashiersTable.id]
            val (rev, cnt) = stats[cId] ?: (0.0 to 0L)
            
            Employer(
                id = cId,
                userId = it[UsersTable.id],
                name = "${it[UsersTable.firstName] ?: ""} ${it[UsersTable.lastName] ?: ""}".trim(),
                phone = it[UsersTable.phoneNumber],
                active = it[PartnerCashiersTable.isActive],
                pointName = it[TradingPointsTable.name],
                tradingPointId = it[TradingPointsTable.id],
                revenue = rev,
                transactionsCount = cnt.toInt()
            )
        }
    }

    suspend fun deleteCashier(cashierId: String) = dbQuery {
        PartnerCashiersTable.deleteWhere { PartnerCashiersTable.id eq cashierId }
    }

    private fun generateUniqueInviteCode(): String {
        repeat(10) {
            val code = "C-" + (100000..999999).random().toString()
            val exists = TradingPointsTable.selectAll().where { TradingPointsTable.inviteCode eq code }.count() > 0
            if (!exists) return code
        }
        throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Failed to generate unique invite code")
    }

    // --- MANAGERS ---
    
    suspend fun generateManagerInvite(partnerId: String): String = dbQuery {
        // Generate or retrieve existing
        val existing = PartnersTable.selectAll().where { PartnersTable.id eq partnerId }
            .map { it[PartnersTable.managerInviteCode] }
            .singleOrNull()
            
        if (existing != null) return@dbQuery existing
        
        // Create new
        val code = "M-" + (100000..999999).random().toString()
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[managerInviteCode] = code
        }
        code
    }

    suspend fun findPartnerByManagerInvite(code: String): String? = dbQuery {
        PartnersTable.selectAll().where { PartnersTable.managerInviteCode eq code }
            .map { it[PartnersTable.id] }
            .singleOrNull()
    }

    suspend fun isUserManager(userId: String, partnerId: String): Boolean = dbQuery {
        !PartnerManagersTable.selectAll().where {
            (PartnerManagersTable.userId eq userId) and (PartnerManagersTable.partnerId eq partnerId)
        }.empty()
    }
    
    suspend fun addManager(userId: String, partnerId: String) = dbQuery {
         PartnerManagersTable.insert {
             it[id] = UUID.randomUUID().toString()
             it[this.userId] = userId
             it[this.partnerId] = partnerId
             it[isActive] = true
         }
    }

    suspend fun getManagers(partnerId: String): List<Employer> = dbQuery {
         // Reuse CashierDto since it has name/phone/active
         PartnerManagersTable.innerJoin(UsersTable)
            .selectAll().where { PartnerManagersTable.partnerId eq partnerId }
            .map {
                Employer(
                    id = it[PartnerManagersTable.id],
                    userId = it[UsersTable.id],
                    name = "${it[UsersTable.firstName] ?: ""} ${it[UsersTable.lastName] ?: ""}".trim(),
                    phone = it[UsersTable.phoneNumber],
                    active = it[PartnerManagersTable.isActive],
                    pointName = null // Manager has no point
                )
            }
    }
    
    suspend fun deleteManager(managerId: String) = dbQuery {
        PartnerManagersTable.deleteWhere { PartnerManagersTable.id eq managerId }
    }
}

@Serializable
data class PartnerEntity(
    val id: String,
    val ownerId: String,
    val name: String,
    val countryCode: String,
    val hasPin: Boolean,
    val status: PartnerStatus,
    val logoUrl: String?,
    val color: String,
    val burnBonusesDays: Int?,
    val downgradeTierDays: Int?,
    val ownerPhone: String? = null
)

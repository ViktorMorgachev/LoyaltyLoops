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
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CountryCode
import io.loyaltyloop.shared.models.Employer
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.LoyaltyProgramType
import io.loyaltyloop.shared.models.LoyaltySettingsDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.PartnerEntity
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.TradingPointDto
import io.loyaltyloop.shared.models.TradingPointSearchResponse
import io.loyaltyloop.shared.models.TradingPointType
import io.loyaltyloop.shared.models.UpdatePartnerRequest
import io.loyaltyloop.shared.models.UpdateTradingPointRequest
import io.loyaltyloop.shared.models.PartnerStatsDto
import io.loyaltyloop.shared.models.WeeklyScheduleDto
import org.jetbrains.exposed.sql.JoinType
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import org.jetbrains.exposed.sql.Op
import java.time.Instant
import java.util.UUID
import kotlin.jvm.Throws
import io.loyaltyloop.server.utils.haversineMeters
import io.loyaltyloop.server.utils.isOpen

class PartnerRepository {

    private val scheduleJson = Json { ignoreUnknownKeys = true }

    suspend fun createPartner(userID: String, request: CreatePartnerRequest): String = dbQuery {
        val alreadyExists = PartnersTable.selectAll().where { PartnersTable.ownerId eq userID }.count() > 0
        if (alreadyExists) {
            throw LoyaltyException(AppErrorCode.BUSINESS_ALREADY_EXISTS, "User already owns a business.")
        }

        if (!SecurityUtils.isStrongPin(request.ownerPin)) {
            throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "PIN must contain 4-12 digits")
        }

        val newPartnerId = UUID.randomUUID().toString()
        val pinHash = SecurityUtils.hashPin(request.ownerPin)

        PartnersTable.insert {
            it[id] = newPartnerId
            it[ownerId] = userID
            it[businessName] = request.businessName
            it[countryCode] = request.countryCode.name
            it[status] = PartnerStatus.PENDING
            it[adminPinHash] = pinHash
            it[defaultVisitsTarget] = 10 // Вынести в конфиги
            if (request.color != null) {
                it[color] = request.color!!
            }
            it[logoUrl] = request.logoUrl
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
        val updated = PartnersTable.update({ PartnersTable.ownerId eq ownerId }) {
            it[businessName] = request.businessName
            it[color] = request.color
            it[logoUrl] = request.logoUrl
            it[burnBonusesDays] = request.burnBonusesDays
            it[downgradeTierDays] = request.downgradeTierDays
            it[defaultVisitsTarget] = request.defaultVisitsTarget
        }

        if (updated > 0) {
            val partnerId = PartnersTable
                .select { PartnersTable.ownerId eq ownerId }
                .map { it[PartnersTable.id] }
                .single()

            LoyaltySettingsTable.update({ LoyaltySettingsTable.partnerId eq partnerId }) {
                it[visitsTarget] = request.defaultVisitsTarget
            }
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

    suspend fun searchPublicPoints(criteria: TradingPointSearchCriteria): TradingPointSearchResponse = dbQuery {
        val now = Instant.now()
        val baseQuery = TradingPointsTable
            .join(PartnersTable, JoinType.INNER, TradingPointsTable.partnerId, PartnersTable.id)
            .select {
                (TradingPointsTable.isActive eq true) and (PartnersTable.status eq PartnerStatus.ACTIVE)
            }

        val filtered = baseQuery.mapNotNull { row ->
            val lat = row[TradingPointsTable.latitude] ?: return@mapNotNull null
            val lon = row[TradingPointsTable.longitude] ?: return@mapNotNull null
            val distance = haversineMeters(criteria.latitude, criteria.longitude, lat, lon)
            if (distance > criteria.radiusMeters) return@mapNotNull null

            val schedule = row.parseSchedule()
            val paused = row[TradingPointsTable.isTemporarilyPaused]
            val isOpenNow = if (paused) false else schedule?.isOpen(now)
            val dto = mapTradingPointEntity(
                row = row,
                schedule = schedule,
                distanceMeters = distance,
                isOpenNow = isOpenNow
            )

            if (!criteria.matches(dto)) return@mapNotNull null
            dto
        }.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }

        val limited = filtered.take(criteria.limit + 1)
        val hasMore = limited.size > criteria.limit
        val finalList = if (hasMore) limited.dropLast(1) else limited

        TradingPointSearchResponse(
            points = finalList,
            total = finalList.size,
            radiusMeters = criteria.radiusMeters,
            limit = criteria.limit,
            hasMore = hasMore
        )
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
            it[this.latitude] = request.latitude
            it[this.longitude] = request.longitude
            it[this.currency] = request.currency.name
            it[this.inviteCode] = generateUniqueInviteCode()
            it[this.workingHoursJson] = request.schedule?.let { scheduleJson.encodeToString<WeeklyScheduleDto>(it) }
            it[this.isTemporarilyPaused] = request.temporarilyPaused
            it[this.contactPhone] = request.contactPhone
            it[this.contactLink] = request.contactLink
            it[this.additionalInfo] = request.additionalInfo

            // По умолчанию точка НЕ АКТИВНА (ждет оплаты)
            it[this.isActive] = false
        }

        // Создаем настройки
        val settingsId = UUID.randomUUID().toString()

        val effectiveVisitsTarget = request.visitsTarget.coerceAtLeast(1)

        LoyaltySettingsTable.insert {
            it[id] = settingsId
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = pointID
            it[programType] = request.programType.name
            it[visitsTarget] = effectiveVisitsTarget
            it[maxBurnPercentage] = 100
            it[awardOnMixedPayment] = request.awardOnMixedPayment
        }

        // Создаем уровни (Если TIERED или HYBRID)
        if (request.programType == LoyaltyProgramType.TIERED_LTV || request.programType == LoyaltyProgramType.HYBRID) {
            val base = request.baseCashback
            val defaultTiers = listOf(
                Triple(1, LoyaltyTierDto.LoyaltyLevel.Base to "Base", base),
                Triple(2, LoyaltyTierDto.LoyaltyLevel.Silver to "Silver", base + 2.0),
                Triple(3, LoyaltyTierDto.LoyaltyLevel.Gold to "Gold", base + 5.0)
            )

            defaultTiers.forEach { (index, tierInfo, percent) ->
                LoyaltyTiersTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[this.settingsId] = settingsId
                    it[levelIndex] = index
                    it[this.name] = tierInfo.second
                    it[threshold] = if (index == 1) 0.0 else (index * 10000.0)
                    it[cashbackPercent] = percent
                }
            }
        }

        pointID
    }

    private fun mapPartnerEntity(row: ResultRow) = PartnerEntity(
        id = row[PartnersTable.id],
        ownerId = row[PartnersTable.ownerId],
        businessName = row[PartnersTable.businessName],
        countryCode = row[PartnersTable.countryCode],
        hasPin = !row[PartnersTable.adminPinHash].isNullOrBlank(),
        status = row[PartnersTable.status],
        logoUrl = row[PartnersTable.logoUrl],
        color = row[PartnersTable.color],
        burnBonusesDays = row[PartnersTable.burnBonusesDays],
        downgradeTierDays = row[PartnersTable.downgradeTierDays],
        defaultVisitsTarget = row[PartnersTable.defaultVisitsTarget],
        ownerPhone = null
    )

    private fun mapTradingPointEntity(
        row: ResultRow,
        schedule: WeeklyScheduleDto? = row.parseSchedule(),
        distanceMeters: Double? = null,
        isOpenNow: Boolean? = null
    ) = TradingPointDto(
        id = row[TradingPointsTable.id],
        name = row[TradingPointsTable.name],
        address = row[TradingPointsTable.address],
        type = row[TradingPointsTable.type],
        latitude = row[TradingPointsTable.latitude],
        longitude = row[TradingPointsTable.longitude],
        active = row[TradingPointsTable.isActive],
        inviteCode = row[TradingPointsTable.inviteCode],
        currency = row[TradingPointsTable.currency],
        schedule = schedule,
        rating = row[TradingPointsTable.rating],
        reviewCount = row[TradingPointsTable.ratingCount],
        distanceMeters = distanceMeters,
        isOpenNow = isOpenNow,
        temporarilyPaused = row[TradingPointsTable.isTemporarilyPaused],
        contactPhone = row[TradingPointsTable.contactPhone],
        contactLink = row[TradingPointsTable.contactLink],
        additionalInfo = row[TradingPointsTable.additionalInfo]
    )

    private fun ResultRow.parseSchedule(): WeeklyScheduleDto? {
        val raw = this[TradingPointsTable.workingHoursJson] ?: return null
        return runCatching { scheduleJson.decodeFromString<WeeklyScheduleDto>(raw) }.getOrNull()
    }

    @Throws(LoyaltyException::class)
    suspend fun getSettingsByPointId(pointId: String): LoyaltySettingsDto = dbQuery {
        val settingsRow = LoyaltySettingsTable.selectAll().where {
            LoyaltySettingsTable.tradingPointId eq pointId
        }.singleOrNull() ?:  throw LoyaltyException(AppErrorCode.LOYALTY_SETTING_NOT_FOUND, "Loyalty Settings not found by point ${pointId}")

        val settingsId = settingsRow[LoyaltySettingsTable.id]

        val tiers = LoyaltyTiersTable.selectAll().where {
            LoyaltyTiersTable.settingsId eq settingsId
        }.map { row ->
            val levelIndex = row[LoyaltyTiersTable.levelIndex]
            val loyaltyLevel = loyaltyLevelFromIndex(levelIndex)
            val loyaltyTier = LoyaltyTierDto.LoyaltyTier(
                level = loyaltyLevel,
                descr = row[LoyaltyTiersTable.name]
            )

            LoyaltyTierDto(
                levelIndex = levelIndex,
                loyaltyTier = loyaltyTier,
                threshold = row[LoyaltyTiersTable.threshold],
                cashbackPercent = row[LoyaltyTiersTable.cashbackPercent]
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
            maxBurnPercentage = settingsRow[LoyaltySettingsTable.maxBurnPercentage],
            awardOnMixedPayment = settingsRow[LoyaltySettingsTable.awardOnMixedPayment]
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

        return@dbQuery hash == SecurityUtils.hashPin(pinInput)
    }

    suspend fun updatePartnerPin(partnerId: String, newPin: String) = dbQuery {
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[adminPinHash] = SecurityUtils.hashPin(newPin)
        }
    }

    suspend fun clearPartnerPin(partnerId: String) = dbQuery {
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[adminPinHash] = null
        }
    }

    suspend fun resetAllPartnerPins(defaultPin: String): Int = dbQuery {
        val hashed = SecurityUtils.hashPin(defaultPin)
        PartnersTable.update({ Op.TRUE }) {
            it[adminPinHash] = hashed
        }
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
            it[type] = request.type
            it[address] = request.address
            it[latitude] = request.latitude
            it[longitude] = request.longitude
            it[currency] = request.currency
            it[workingHoursJson] = request.schedule?.let { scheduleJson.encodeToString<WeeklyScheduleDto>(it) }
            it[isTemporarilyPaused] = request.temporarilyPaused
            it[contactPhone] = request.contactPhone
            it[contactLink] = request.contactLink
            it[additionalInfo] = request.additionalInfo
        }

        val settingsReq = request.settings

        // 2. Find Settings row and partner
        val settingsRow = LoyaltySettingsTable
            .selectAll().where { LoyaltySettingsTable.tradingPointId eq pointId }
            .single()
        val settingsId = settingsRow[LoyaltySettingsTable.id]
        val partnerId = settingsRow[LoyaltySettingsTable.partnerId]
        val effectiveVisitsTarget = getDefaultVisitsTarget(partnerId)

        // 3. Update Settings
        LoyaltySettingsTable.update({ LoyaltySettingsTable.id eq settingsId }) {
            it[programType] = settingsReq.programType.name
            it[visitsTarget] = effectiveVisitsTarget
            it[maxBurnPercentage] = settingsReq.maxBurnPercentage
            it[awardOnMixedPayment] = settingsReq.awardOnMixedPayment
        }

        // 4. Re-create Tiers (Simple approach: delete all, insert new)
        LoyaltyTiersTable.deleteWhere { LoyaltyTiersTable.settingsId eq settingsId }

        if (settingsReq.programType == LoyaltyProgramType.TIERED_LTV || settingsReq.programType == LoyaltyProgramType.HYBRID) {
            settingsReq.tiers.forEach { tier ->
                LoyaltyTiersTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[this.settingsId] = settingsId
                    it[levelIndex] = tier.levelIndex
                    it[name] = tier.loyaltyTier.descr ?: tier.loyaltyTier.level.name
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

    private fun getDefaultVisitsTarget(partnerId: String): Int =
        PartnersTable
            .select { PartnersTable.id eq partnerId }
            .map { it[PartnersTable.defaultVisitsTarget] }
            .single()

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

private fun loyaltyLevelFromIndex(index: Int): LoyaltyTierDto.LoyaltyLevel {
    return when (index) {
        1 -> LoyaltyTierDto.LoyaltyLevel.Base
        2 -> LoyaltyTierDto.LoyaltyLevel.Silver
        else -> LoyaltyTierDto.LoyaltyLevel.Gold
    }
}

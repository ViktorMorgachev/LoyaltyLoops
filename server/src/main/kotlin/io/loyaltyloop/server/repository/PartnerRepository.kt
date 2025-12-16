package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.*
import io.loyaltyloop.server.models.TradingPointSearchCriteria
import io.loyaltyloop.server.models.matches
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.server.utils.haversineMeters
import io.loyaltyloop.server.utils.isOpen
import io.loyaltyloop.shared.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.UUID
import kotlin.jvm.Throws

class PartnerRepository {

    private val scheduleJson = Json { ignoreUnknownKeys = true }

    // --- PARTNER LIFECYCLE ---

    suspend fun createPartner(userID: String, request: CreatePartnerRequest): String = dbQuery {
        val alreadyExists = PartnersTable.select { PartnersTable.ownerId eq userID }.count() > 0
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
            it[defaultVisitsTarget] = 10
            it[color] = request.color ?: "#4F46E5"
            it[logoUrl] = request.logoUrl
            it[baseCurrency] = request.baseCurrency
        }

        newPartnerId
    }

    /**
     * Finds a partner owned by the user.
     * Returns null if not found.
     */
    suspend fun getPartnerByOwnerId(ownerId: String): PartnerEntity? = dbQuery {
        val row = PartnersTable.selectAll()
            .where { PartnersTable.ownerId eq ownerId }
            .singleOrNull() ?: return@dbQuery null

        val baseEntity = rowToPartnerEntity(row)

        // Warnings are relevant for owners
        val warnings = getExpiringPointsForPartner(baseEntity.id)
        baseEntity.copy(subscriptionWarnings = warnings)
    }

    /**
     * Finds a partner where the user is a manager.
     * Returns null if not found.
     */
    suspend fun getPartnerByManagerId(managerId: String): PartnerEntity? = dbQuery {
        val row = PartnersTable.innerJoin(PartnerManagersTable)
            .selectAll()
            .where { (PartnerManagersTable.userId eq managerId) and (PartnerManagersTable.isActive eq true) }
            .singleOrNull() ?: return@dbQuery null

        rowToPartnerEntity(row)
    }

    /**
     * Convenience method: Get partner by ID
     */
    suspend fun getPartnerByIdQ(partnerID: String): PartnerEntity = dbQuery {
        val row = PartnersTable.selectAll()
            .where { PartnersTable.id eq partnerID }
            .singleOrNull()
            ?: throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND, "Partner not found")

        rowToPartnerEntity(row)
    }

    /**
     * Unified method to get the partner associated with a user (either as Owner or Manager).
     * Throws BUSINESS_NOT_FOUND if neither.
     */
    @Throws(LoyaltyException::class)
    suspend fun getPartnerForMember(userId: String): PartnerEntity {
        return getPartnerByOwnerId(userId)
            ?: getPartnerByManagerId(userId)
            ?: throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND, "Partner not found for this user")
    }

    suspend fun updatePartner(ownerId: String, request: UpdatePartnerRequest) = dbQuery {
        val updated = PartnersTable.update({ PartnersTable.ownerId eq ownerId }) {
            it[businessName] = request.businessName
            it[color] = request.color
            it[logoUrl] = request.logoUrl
            it[burnBonusesDays] = request.burnBonusesDays
            it[downgradeTierDays] = request.downgradeTierDays
            it[defaultVisitsTarget] = request.defaultVisitsTarget
            it[baseCurrency] = request.baseCurrency
        }

        // Синхронизируем дефолтную цель визитов с настройками
        if (updated > 0) {
            val partnerId = PartnersTable.slice(PartnersTable.id)
                .select { PartnersTable.ownerId eq ownerId }
                .single()[PartnersTable.id]

            LoyaltySettingsTable.update({ LoyaltySettingsTable.partnerId eq partnerId }) {
                it[visitsTarget] = request.defaultVisitsTarget
            }
        }
    }

    suspend fun getAllPartners(): List<PartnerEntity> = dbQuery {
        PartnersTable.innerJoin(UsersTable)
            .selectAll()
            .map { rowToPartnerEntity(it).copy(ownerPhone = it[UsersTable.phoneNumber]) }
    }

    suspend fun updateStatus(partnerId: String, newStatus: PartnerStatus) = dbQuery {
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[status] = newStatus
        }
    }

    suspend fun clearPartnerPin(partnerId: String) = dbQuery {
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[adminPinHash] = null
        }
    }

    // --- TRADING POINTS MANAGEMENT ---

    suspend fun getPointsByPartnerId(partnerId: String): List<TradingPointDto> = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.partnerId eq partnerId }
            .map { mapTradingPointEntity(it) }
    }

    suspend fun getPointById(pointId: String): TradingPointDto = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.id eq pointId }
            .map { mapTradingPointEntity(it) }
            .singleOrNull() ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND)
    }

    suspend fun searchPublicPoints(criteria: TradingPointSearchCriteria): TradingPointSearchResponse = dbQuery {
        val now = Instant.now()

        // 1. Выбираем только активные точки активных партнеров
        val query = TradingPointsTable.innerJoin(PartnersTable)
            .selectAll()
            .where {
                (TradingPointsTable.isActive eq true) and
                        (PartnersTable.status eq PartnerStatus.ACTIVE)
            }

        // 2. Фильтрация In-Memory (Haversine distance)
        val filtered = query.mapNotNull { row ->
            val lat = row[TradingPointsTable.latitude]
            val lon = row[TradingPointsTable.longitude]

            if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) return@mapNotNull null

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

        // 3. Пагинация
        val fromIndex = 0
        val toIndex = (criteria.limit + 1).coerceAtMost(filtered.size)
        val limited = filtered.subList(fromIndex, toIndex)

        val hasMore = limited.size > criteria.limit
        val finalList = if (hasMore) limited.dropLast(1) else limited

        TradingPointSearchResponse(
            points = finalList,
            total = filtered.size,
            radiusMeters = criteria.radiusMeters,
            limit = criteria.limit,
            hasMore = hasMore
        )
    }

    // Создание точки + Настроек + Уровней
    suspend fun createTradingPoint(partnerId: String, request: CreateTradingPointRequest) = dbQuery {
        val pointID = UUID.randomUUID().toString()

        TradingPointsTable.insert {
            it[id] = pointID
            it[this.partnerId] = partnerId
            it[name] = request.name
            it[type] = request.type
            it[address] = request.address
            it[latitude] = request.latitude
            it[longitude] = request.longitude
            it[currency] = request.currency.name
            it[inviteCode] = generateUniqueInviteCode()
            it[workingHoursJson] = request.schedule?.let { s -> scheduleJson.encodeToString<WeeklyScheduleDto>(s) }
            it[isTemporarilyPaused] = request.temporarilyPaused
            it[contactPhone] = request.contactPhone
            it[contactLink] = request.contactLink
            it[additionalInfo] = request.additionalInfo
            it[isActive] = false // Ждет оплаты
            it[rating] = 0.0
            it[ratingCount] = 0
            it[timezone] = request.timezone
        }

        // DRY: Используем хелпер для создания настроек
        createDefaultSettingsAndTiers(pointID, partnerId, request.programType, request.visitsTarget, request.baseCashback, request.awardOnMixedPayment)

        pointID
    }

    // Обновление точки + Настроек + Уровней
    suspend fun updateTradingPoint(pointId: String, request: UpdateTradingPointRequest) = dbQuery {
        // 1. Update Point
        TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
            it[name] = request.name
            it[type] = request.type
            it[address] = request.address
            it[latitude] = request.latitude
            it[longitude] = request.longitude
            it[currency] = request.currency
            it[workingHoursJson] = request.schedule?.let { s -> scheduleJson.encodeToString<WeeklyScheduleDto>(s) }
            it[isTemporarilyPaused] = request.temporarilyPaused
            it[contactPhone] = request.contactPhone
            it[contactLink] = request.contactLink
            it[additionalInfo] = request.additionalInfo
            it[timezone] = request.timezone
        }

        // 2. Find Settings ID
        val settingsRow = LoyaltySettingsTable.select { LoyaltySettingsTable.tradingPointId eq pointId }.single()
        val settingsId = settingsRow[LoyaltySettingsTable.id]
        val partnerId = settingsRow[LoyaltySettingsTable.partnerId]

        val defaultTarget = PartnersTable.slice(PartnersTable.defaultVisitsTarget)
            .select { PartnersTable.id eq partnerId }
            .single()[PartnersTable.defaultVisitsTarget]

        // 3. Update Settings
        LoyaltySettingsTable.update({ LoyaltySettingsTable.id eq settingsId }) {
            it[programType] = request.settings.programType.name
            it[visitsTarget] = defaultTarget
            it[maxBurnPercentage] = request.settings.maxBurnPercentage
            it[awardOnMixedPayment] = request.settings.awardOnMixedPayment
        }

        // 4. Re-create Tiers
        LoyaltyTiersTable.deleteWhere { LoyaltyTiersTable.settingsId eq settingsId }

        if (request.settings.programType == LoyaltyProgramType.TIERED_LTV || request.settings.programType == LoyaltyProgramType.HYBRID) {
            request.settings.tiers.forEach { tier ->
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

    // Soft Delete: не удаляем, а выключаем
    suspend fun deleteTradingPoint(pointId: String) = dbQuery {
        TradingPointsTable.deleteWhere { TradingPointsTable.id eq pointId }
    }

    suspend fun updatePointStatus(pointId: String, isActive: Boolean) = dbQuery {
        TradingPointsTable.update({ TradingPointsTable.id eq pointId }) {
            it[this.isActive] = isActive
        }
    }

    // --- CASHIERS ---

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

    suspend fun getAllCashiers(partnerId: String): List<Employer> = dbQuery {
        // 1. Получаем список кассиров с данными пользователей и точек
        val cashiers = PartnerCashiersTable
            .innerJoin(UsersTable)
            .innerJoin(TradingPointsTable)
            .selectAll().where { TradingPointsTable.partnerId eq partnerId }
            .toList()

        if (cashiers.isEmpty()) return@dbQuery emptyList()

        val cashierIds = cashiers.map { it[PartnerCashiersTable.id] }

        // 2. Считаем статистику через SQL Aggregation (Оптимизация)
        val sumAmount = TransactionsHistoryTable.amount.sum()
        val countTx = TransactionsHistoryTable.id.count()

        val stats = TransactionsHistoryTable
            .slice(TransactionsHistoryTable.cashierId, sumAmount, countTx)
            .select { TransactionsHistoryTable.cashierId inList cashierIds }
            .groupBy(TransactionsHistoryTable.cashierId)
            .associate { row ->
                val cId = row[TransactionsHistoryTable.cashierId]
                val rev = row[sumAmount] ?: 0.0
                val cnt = row[countTx]
                cId to (rev to cnt)
            }

        // 3. Маппим
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
                    tradingPointId = it[PartnerCashiersTable.tradingPointId]
                )
            }
    }

    suspend fun deleteCashier(cashierId: String) = dbQuery {
        // Кассиров удаляем полностью (Hard Delete), чтобы освободить роль
        PartnerCashiersTable.deleteWhere { PartnerCashiersTable.id eq cashierId }
    }

    // --- MANAGERS ---

    suspend fun generateManagerInvite(partnerId: String): String = dbQuery {
        val existing = PartnersTable.slice(PartnersTable.managerInviteCode)
            .select { PartnersTable.id eq partnerId }
            .singleOrNull()?.get(PartnersTable.managerInviteCode)

        if (existing != null) return@dbQuery existing

        repeat(15) {
            val code = "M-" + (100000..999999).random().toString()

            // Проверяем, занят ли этот код КЕМ-ЛИБО в таблице партнеров
            // Используем count() или empty(), это очень быстрый запрос по индексу
            val isTaken = PartnersTable.select { PartnersTable.managerInviteCode eq code }.count() > 0

            if (!isTaken) {
                // Код свободен! Записываем его.
                PartnersTable.update({ PartnersTable.id eq partnerId }) {
                    it[managerInviteCode] = code
                }
                return@dbQuery code
            }
        }

        // Если за 15 попыток мы все время попадали в занятые коды (почти невозможно, если база не переполнена)
        throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Failed to generate unique manager invite code. Please try again.")
    }

    suspend fun findPartnerByManagerInvite(code: String): String? = dbQuery {
        PartnersTable.slice(PartnersTable.id)
            .select { PartnersTable.managerInviteCode eq code }
            .singleOrNull()?.get(PartnersTable.id)
    }

    suspend fun addManager(userId: String, partnerId: String) = dbQuery {
        PartnerManagersTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[this.userId] = userId
            it[this.partnerId] = partnerId
            it[isActive] = true
        }
    }

    suspend fun isUserManager(userId: String, partnerId: String): Boolean = dbQuery {
        !PartnerManagersTable.selectAll().where {
            (PartnerManagersTable.userId eq userId) and (PartnerManagersTable.partnerId eq partnerId)
        }.empty()
    }

    suspend fun isManagerRecordOfPartner(managerRecordId: String, partnerId: String): Boolean = dbQuery {
        !PartnerManagersTable.selectAll().where {
            (PartnerManagersTable.id eq managerRecordId) and (PartnerManagersTable.partnerId eq partnerId)
        }.empty()
    }

    suspend fun isCassierRecordOfPartner(cashierId: String, partnerId: String): Boolean = dbQuery {
        !PartnerCashiersTable.selectAll().where {
            (PartnerCashiersTable.id eq cashierId) and (PartnerCashiersTable.partnerId eq partnerId)
        }.empty()
    }

    suspend fun getManagers(partnerId: String): List<Employer> = dbQuery {
        PartnerManagersTable.innerJoin(UsersTable)
            .selectAll().where { PartnerManagersTable.partnerId eq partnerId }
            .map {
                Employer(
                    id = it[PartnerManagersTable.id],
                    userId = it[UsersTable.id],
                    name = "${it[UsersTable.firstName] ?: ""} ${it[UsersTable.lastName] ?: ""}".trim(),
                    phone = it[UsersTable.phoneNumber],
                    active = it[PartnerManagersTable.isActive]
                )
            }
    }

    suspend fun deleteManager(managerId: String) = dbQuery {
        PartnerManagersTable.deleteWhere { PartnerManagersTable.id eq managerId }
    }

    // --- SECURITY & STATS ---

    suspend fun verifyPartnerPin(partnerId: String, pinInput: String): Boolean = dbQuery {
        val row = PartnersTable.slice(PartnersTable.adminPinHash, PartnersTable.ownerId)
            .select { PartnersTable.id eq partnerId }
            .singleOrNull() ?: return@dbQuery false

        val hash = row[PartnersTable.adminPinHash]

        if (hash == null) {
            val ownerId = row[PartnersTable.ownerId]
            val ownerPhone = UsersTable.slice(UsersTable.phoneNumber)
                .select { UsersTable.id eq ownerId }
                .singleOrNull()?.get(UsersTable.phoneNumber) ?: return@dbQuery false

            return@dbQuery pinInput == ownerPhone.takeLast(4)
        }

        return@dbQuery hash == SecurityUtils.hashPin(pinInput)
    }

    suspend fun updatePartnerPin(partnerId: String, newPin: String) = dbQuery {
        PartnersTable.update({ PartnersTable.id eq partnerId }) {
            it[adminPinHash] = SecurityUtils.hashPin(newPin)
        }
    }

    suspend fun getPartnerStats(partnerId: String): PartnerStatsDto = dbQuery {
        val pointsCount = TradingPointsTable.select { TradingPointsTable.partnerId eq partnerId }.count()
        val cardsCount = LoyaltyCardTable.select { LoyaltyCardTable.partnerId eq partnerId }.count()

        val transactionsCount = TransactionsHistoryTable
            .join(
                otherTable = TradingPointsTable,
                joinType = JoinType.INNER,
                onColumn = TransactionsHistoryTable.tradingPointId,
                otherColumn = TradingPointsTable.id
            )
            .select { TradingPointsTable.partnerId eq partnerId }
            .count()

        PartnerStatsDto(
            partnerId = partnerId,
            pointsCount = pointsCount.toInt(),
            cardsCount = cardsCount.toInt(),
            transactionsCount = transactionsCount.toInt()
        )
    }

    // --- HELPERS (PRIVATE) ---

    // DRY Helper: используется при создании точки
    private fun createDefaultSettingsAndTiers(
        pointID: String,
        partnerId: String,
        programType: LoyaltyProgramType,
        visitsTarget: Int,
        baseCashback: Double,
        awardMixed: Boolean
    ) {
        val settingsId = UUID.randomUUID().toString()
        LoyaltySettingsTable.insert {
            it[id] = settingsId
            it[this.partnerId] = partnerId
            it[this.tradingPointId] = pointID
            it[this.programType] = programType.name
            it[this.visitsTarget] = visitsTarget.coerceAtLeast(1)
            it[this.maxBurnPercentage] = 100
            it[this.awardOnMixedPayment] = awardMixed
        }

        if (programType == LoyaltyProgramType.TIERED_LTV || programType == LoyaltyProgramType.HYBRID) {
            listOf(
                Triple(1, "Base", baseCashback),
                Triple(2, "Silver", baseCashback + 2.0),
                Triple(3, "Gold", baseCashback + 5.0)
            ).forEach { (idx, name, pct) ->
                LoyaltyTiersTable.insert {
                    it[id] = UUID.randomUUID().toString()
                    it[this.settingsId] = settingsId
                    it[levelIndex] = idx
                    it[this.name] = name
                    it[threshold] = if (idx == 1) 0.0 else (idx * 10000.0)
                    it[cashbackPercent] = pct
                }
            }
        }
    }

    private fun rowToPartnerEntity(row: ResultRow): PartnerEntity {
        return PartnerEntity(
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
            ownerPhone = null,
            subscriptionWarnings = null,
            baseCurrency = row[PartnersTable.baseCurrency]
        )
    }

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
        latitude = row[TradingPointsTable.latitude] ?: 0.0,
        longitude = row[TradingPointsTable.longitude] ?: 0.0,
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
        additionalInfo = row[TradingPointsTable.additionalInfo],
        timezone = row[TradingPointsTable.timezone]
        )

    private fun ResultRow.parseSchedule(): WeeklyScheduleDto? {
        val raw = this[TradingPointsTable.workingHoursJson] ?: return null
        return runCatching { scheduleJson.decodeFromString<WeeklyScheduleDto>(raw) }.getOrNull()
    }

    private fun generateUniqueInviteCode(): String {
        repeat(10) {
            val code = "C-" + (100000..999999).random().toString()
            val exists = TradingPointsTable.select { TradingPointsTable.inviteCode eq code }.count() > 0
            if (!exists) return code
        }
        throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Failed to generate unique invite code")
    }

    // Вспомогательный метод для getPartnerByUserId
    private fun getExpiringPointsForPartner(partnerId: String): List<ExpiringPointDto>? {
        val now = System.currentTimeMillis()
        val warningThreshold = now + (3 * 24 * 60 * 60 * 1000L)

        val allSubs = PlatformSubscriptionsTable
            .innerJoin(TradingPointsTable)
            .slice(TradingPointsTable.id, TradingPointsTable.name, PlatformSubscriptionsTable.endDate)
            .select {
                (TradingPointsTable.partnerId eq partnerId) and
                        (PlatformSubscriptionsTable.isActive eq true) and
                        (TradingPointsTable.isActive eq true) and
                        (PlatformSubscriptionsTable.endDate.isNotNull())
            }
            .map { Triple(it[TradingPointsTable.id], it[TradingPointsTable.name], it[PlatformSubscriptionsTable.endDate]!!) }

        if (allSubs.isEmpty()) return null

        val maxDates = allSubs.groupBy { it.first }
            .mapValues { (_, list) -> list.maxOf { it.third } }

        val warnings = maxDates.filter { (_, maxDate) ->
            maxDate > now && maxDate <= warningThreshold
        }.map { (pointId, maxDate) ->
            val name = allSubs.first { it.first == pointId }.second
            ExpiringPointDto(pointName = name, endDate = maxDate)
        }

        return warnings.ifEmpty { null }
    }

    // Helper для получения ID партнера по ID точки
    suspend fun getPartnerIdByPoint(pointId: String): String = dbQuery {
        TradingPointsTable.slice(TradingPointsTable.partnerId)
            .select { TradingPointsTable.id eq pointId }
            .singleOrNull()?.get(TradingPointsTable.partnerId)
            ?: throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Point has no partner")
    }

    // Helper для инвайтов
    suspend fun findTradingPointByInvite(code: String): TradingPointDto = dbQuery {
        TradingPointsTable.selectAll().where { TradingPointsTable.inviteCode eq code }
            .map { mapTradingPointEntity(it) }
            .singleOrNull() ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND, "Point not found by invite")
    }

    // Helper для isUserCashierAtPoint
    suspend fun isUserCashierAtPoint(userId: String, pointId: String): Boolean = dbQuery {
        !PartnerCashiersTable.selectAll().where {
            (PartnerCashiersTable.userId eq userId) and (PartnerCashiersTable.tradingPointId eq pointId)
        }.empty()
    }

    private fun loyaltyLevelFromIndex(index: Int): LoyaltyTierDto.LoyaltyLevel {
        return when (index) {
            1 -> LoyaltyTierDto.LoyaltyLevel.Base
            2 -> LoyaltyTierDto.LoyaltyLevel.Silver
            else -> LoyaltyTierDto.LoyaltyLevel.Gold
        }
    }

    // Этот метод нужен для getSettingsByPointId, который должен быть тут (или в отдельном LoyaltyRepo)
    // Учитывая "не удалять методы", я его вернул.
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
}
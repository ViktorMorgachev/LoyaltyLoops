package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.LoyaltyTiersTable
import io.loyaltyloop.server.database.tables.PartnerStaffTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.TransactionsHistoryTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.models.getDefaultTiers
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.SecurityUtils
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toPartnerEntity
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CreatePartnerRequest
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.PartnerEntity
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.UpdatePartnerRequest
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.indexToLoyaltyLevel
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

// TODO checked
class PartnerRepository(private val subscriptionRepository: SubscriptionRepository) {

    suspend fun getPartnerByIdOrThrow(partnerId: String, loadOtherData: Boolean = true): PartnerEntity = dbQuery {
        val uuid = try {
            partnerId.toUUID()
        } catch (e: LoyaltyException) {
            throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "PartnerId invalid", e)
        }

        val row = PartnersTable
            .selectAll()
            .where { PartnersTable.id eq uuid }
            .singleOrNull()
            ?:  throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND, "Partner not found")

        val baseEntity = row.toPartnerEntity()

        val tiers = LoyaltyTiersTable
            .select { LoyaltyTiersTable.partner eq uuid }
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

        val entityWithTiers = baseEntity.copy(tiers = tiers)

        if (loadOtherData){
            val phone = UsersTable
                .slice(UsersTable.phoneNumber)
                .select { UsersTable.id eq row[PartnersTable.owner] }
                .singleOrNull()
                ?.get(UsersTable.phoneNumber)

            entityWithTiers.copy(ownerPhone = phone)
        } else {
            entityWithTiers
        }

    }

    suspend fun getPartnerIdByPointId(pointId: String): String = dbQuery {
        val pointUuid = pointId.toUUID()

        TradingPointsTable
            .slice(TradingPointsTable.partner) // Колонка-ссылка
            .select { TradingPointsTable.id eq pointUuid }
            .singleOrNull()
            ?.get(TradingPointsTable.partner)?.value?.toString()
            ?: throw LoyaltyException(AppErrorCode.POINT_NOT_FOUND, "Trading point not found")
    }

    suspend fun getPartnerByPointId(pointId: String): PartnerEntity? = dbQuery {
        val pointUuid = pointId.toUUID()

        val partnerId = TradingPointsTable
            .slice(TradingPointsTable.partner)
            .select { TradingPointsTable.id eq pointUuid }
            .singleOrNull()
            ?.get(TradingPointsTable.partner)?.value

        if (partnerId == null) return@dbQuery null

        val row = PartnersTable
            .selectAll()
            .where { PartnersTable.id eq partnerId }
            .singleOrNull()
            ?: return@dbQuery null

        row.toPartnerEntity()
    }

    suspend fun getPartnerByManagerId(userId: String): PartnerEntity? = dbQuery {
        val userUuid = userId.toUUID()
        PartnersTable
            .innerJoin(PartnerStaffTable) // JOIN с единой таблицей персонала
            .selectAll()
            .where {
                (PartnerStaffTable.user eq userUuid) and
                        (PartnerStaffTable.role eq UserRole.PARTNER_MANAGER) and
                        (PartnerStaffTable.isActive eq true)
            }
            .singleOrNull()?.toPartnerEntity() ?: return@dbQuery null
    }

    suspend fun getPartnersOwnedByUser(userId: String): List<PartnerEntity> {
        val partners = dbQuery {
            val userUuid = userId.toUUID()
            PartnersTable
                .selectAll()
                .where { PartnersTable.owner eq userUuid }
                .map { row -> row.toPartnerEntity() }
        }

        return partners.map { partner ->
            partner.copy(
                subscriptionWarnings = subscriptionRepository.getExpiringPointsForPartner(partner.id)
            )
        }
    }

    suspend fun getPartnerOwnedByUser(userId: String, partnerId: String): PartnerEntity? {
        val partner = dbQuery {
            val userUuid = userId.toUUID()
            val partnerUuid = partnerId.toUUID()

            PartnersTable
                .selectAll()
                .where { PartnersTable.id eq partnerUuid and (PartnersTable.owner eq userUuid) }
                .singleOrNull()?.toPartnerEntity()
        } ?: return null

        return partner.copy(
            subscriptionWarnings = subscriptionRepository.getExpiringPointsForPartner(partner.id)
        )
    }


    suspend fun createPartner(
        userId: String,
        timeZone: String,
        request: CreatePartnerRequest
    ): String = dbQuery {
        val userUuid = userId.toUUID()

        // Проверка на дубликат бизнеса у одного владельца
        val alreadyExists = PartnersTable
            .select { PartnersTable.owner eq userUuid }
            .count() > 0

        if (alreadyExists) {
            throw LoyaltyException(AppErrorCode.BUSINESS_ALREADY_EXISTS, "User already owns a business.")
        }

        val pinHash = SecurityUtils.hashPin(request.ownerPin)

        val newPartnerId = PartnersTable.insertAndGetId {
            it[owner] = userUuid
            it[businessName] = request.businessName
            it[countryCode] = request.countryCode.name
            it[baseCurrency] = request.baseCurrency
            it[status] = PartnerStatus.PENDING
            it[adminPinHash] = pinHash
            it[defaultVisitsTarget] = 10
            it[color] = request.color ?: "#4F46E5"
            it[logoUrl] = request.logoUrl
            it[timezone] = timeZone
        }

        LoyaltyTiersTable.batchInsert(
            getDefaultTiers
        ) { def ->
            this[LoyaltyTiersTable.partner] = newPartnerId
            this[LoyaltyTiersTable.levelIndex] = def.index
            this[LoyaltyTiersTable.name] = def.name
            this[LoyaltyTiersTable.threshold] = def.threshold
            this[LoyaltyTiersTable.cashbackPercent] = def.cashback
        }

        newPartnerId.value.toString()
    }

    suspend fun updatePartner(
        partnerId: String,
        timeZone: String,
        request: UpdatePartnerRequest
    ) = dbQuery {
        val partnerUuid = partnerId.toUUID()

        val existingCurrency = PartnersTable.slice(PartnersTable.baseCurrency)
            .select { PartnersTable.id eq partnerUuid }
            .singleOrNull()
            ?.get(PartnersTable.baseCurrency)

        if (existingCurrency != null && request.baseCurrency != existingCurrency) {

            val hasTradingPoints = TradingPointsTable
                .select { TradingPointsTable.partner eq partnerUuid }
                .count() > 0

            if (hasTradingPoints) {
                val sixMonthsAgo = nowUtc().minusMonths(6)
                val recentTx = TransactionsHistoryTable
                    .innerJoin(TradingPointsTable)
                    .select {
                        (TradingPointsTable.partner eq partnerUuid) and
                                (TransactionsHistoryTable.createdAt greaterEq sixMonthsAgo)
                    }
                    .count()

                if (recentTx > 0) {
                    throw LoyaltyException(AppErrorCode.FORBIDDEN, "Cannot change currency with active history.")
                }
            }
        }

        PartnersTable.update({ PartnersTable.id eq partnerUuid }) {
            it[businessName] = request.businessName
            it[timezone] = timeZone
            it[color] = request.color
            it[logoUrl] = request.logoUrl
            it[burnBonusesDays] = request.burnBonusesDays
            it[downgradeTierDays] = request.downgradeTierDays
            it[defaultVisitsTarget] = request.defaultVisitsTarget
            it[baseCurrency] = request.baseCurrency
        }

        request.tiers?.let { newTiers ->
            newTiers.forEach { tier ->
                LoyaltyTiersTable.update({ (LoyaltyTiersTable.partner eq partnerUuid) and (LoyaltyTiersTable.levelIndex eq tier.levelIndex) }) {
                    it[threshold] = tier.threshold.toBigDecimal()
                    it[cashbackPercent] = tier.cashbackPercent.toBigDecimal()
                }
            }
        }
    }

    suspend fun getAllPartners(limit: Int = 100, offset: Long = 0): List<PartnerEntity> = dbQuery {
        PartnersTable
            .innerJoin(UsersTable) // Джойним, чтобы достать телефон владельца
            .selectAll()
            .limit(limit, offset)
            .map { row ->
                row.toPartnerEntity().copy(
                    // Достаем телефон из таблицы юзеров
                    ownerPhone = row[UsersTable.phoneNumber]
                )
            }
    }

    suspend fun clearPartnerPin(partnerId: String) = dbQuery {
        val partnerUuid = partnerId.toUUID()
        PartnersTable.update({ PartnersTable.id eq partnerUuid }) {
            it[adminPinHash] = null
        }
    }

    suspend fun findPartnerByManagerInvite(code: String): String? = dbQuery {
        PartnersTable
            .slice(PartnersTable.id)
            .select { PartnersTable.managerInviteCode eq code }
            .singleOrNull()
            ?.get(PartnersTable.id)?.value?.toString()
    }

    suspend fun getManagerInvite(partnerId: String): String = dbQuery {
        val partnerUuid = partnerId.toUUID()

        PartnersTable
            .slice(PartnersTable.managerInviteCode)
            .select { PartnersTable.owner eq partnerUuid }
            .singleOrNull()
            ?.get(PartnersTable.managerInviteCode)
            ?: throw LoyaltyException(AppErrorCode.BUSINESS_NOT_FOUND, "Partner not found")
    }


    suspend fun verifyPartnerPin(partnerId: String, pinInput: String): Boolean = dbQuery {
        val partnerUuid = partnerId.toUUID()

        val row = PartnersTable
            .slice(PartnersTable.adminPinHash, PartnersTable.owner)
            .select { PartnersTable.id eq partnerUuid } // Ищем по ID бизнеса
            .singleOrNull()
            ?: return@dbQuery false

        val hash = row[PartnersTable.adminPinHash]

        if (hash != null) {
            return@dbQuery SecurityUtils.verifyPin(pinInput, hash)
        }

        val ownerId = row[PartnersTable.owner] // Это EntityID<UUID>

        val ownerPhone = UsersTable
            .slice(UsersTable.phoneNumber)
            .select { UsersTable.id eq ownerId }
            .singleOrNull()
            ?.get(UsersTable.phoneNumber)
            ?: return@dbQuery false

        return@dbQuery pinInput == ownerPhone.takeLast(4)
    }

    suspend fun updatePartnerPin(partnerId: String, newPin: String) = dbQuery {
        val partnerUuid = partnerId.toUUID()

        if (!SecurityUtils.isStrongPin(newPin)) {
            throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "PIN is too simple")
        }

        PartnersTable.update({ PartnersTable.id eq partnerUuid }) {
            it[adminPinHash] = SecurityUtils.hashPin(newPin)
        }
    }
}

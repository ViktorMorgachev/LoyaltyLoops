package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnerStaffTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.Employer
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.util.UUID

// TODO checked
class PartnerStaffRepository {

    suspend fun getCassierById(userId: String, pointId: String): UUID? = dbQuery {
        val userUuid = userId.toUUID()
        val pointUuid = pointId.toUUID()

        PartnerStaffTable
            .slice(PartnerStaffTable.id)
            .select {
                (PartnerStaffTable.user eq userUuid) and
                        (PartnerStaffTable.tradingPoint eq pointUuid) and
                        (PartnerStaffTable.isActive eq true)
            }
            .singleOrNull()
            ?.get(PartnerStaffTable.id)?.value
    }

    // --- CASHIERS ---
    suspend fun getAllCashiers(partnerId: String): List<Employer> = dbQuery {
        val partnerUuid = partnerId.toUUID()

        val staffRows = PartnerStaffTable
            .innerJoin(UsersTable)
            .innerJoin(TradingPointsTable)
            .select {
                (PartnerStaffTable.partner eq partnerUuid) and
                        (PartnerStaffTable.role eq UserRole.CASHIER) and
                        (PartnerStaffTable.isActive eq true)
            }
            .toList()

        if (staffRows.isEmpty()) return@dbQuery emptyList()

        staffRows.map { row ->
            val userId = row[UsersTable.id].value.toString()
            val firstName = row[UsersTable.firstName] ?: ""
            val lastName = row[UsersTable.lastName] ?: ""

            Employer(
                id = row[PartnerStaffTable.id].value.toString(),
                userId = userId,
                name = "$firstName $lastName".trim(),
                phone = row[UsersTable.phoneNumber],
                active = row[PartnerStaffTable.isActive],
                pointName = row[TradingPointsTable.name],
                tradingPointId = row[TradingPointsTable.id].value.toString(),
                revenue = 0.0,
                transactionsCount = 0
            )
        }
    }

    suspend fun getCashiersByPoint(pointId: String): List<Employer> = dbQuery {
        val pointUuid = pointId.toUUID()

        PartnerStaffTable
            .innerJoin(UsersTable)
            .select {
                (PartnerStaffTable.tradingPoint eq pointUuid) and
                        (PartnerStaffTable.role eq UserRole.CASHIER) and
                        (PartnerStaffTable.isActive eq true)
            }
            .map { row ->
                val firstName = row[UsersTable.firstName] ?: ""
                val lastName = row[UsersTable.lastName] ?: ""

                Employer(
                    id = row[PartnerStaffTable.id].value.toString(),
                    userId = row[UsersTable.id].value.toString(),
                    name = "$firstName $lastName".trim(),
                    phone = row[UsersTable.phoneNumber],
                    active = row[PartnerStaffTable.isActive],
                    tradingPointId = pointId,
                )
            }

    }

    // --- MANAGERS ---
    suspend fun isHasManagerOfPartner(userId: String, partnerId: String): Boolean = dbQuery {
        val userUuid = userId.toUUID()
        val partnerUuid = partnerId.toUUID()

        PartnerStaffTable.select {
            (PartnerStaffTable.user eq userUuid) and
                    (PartnerStaffTable.partner eq partnerUuid) and
                    (PartnerStaffTable.role eq UserRole.PARTNER_MANAGER) and
                    (PartnerStaffTable.isActive eq true)
        }.any()
    }

    suspend fun isHasCassierOfPartner(userId: String, partnerId: String): Boolean = dbQuery {
        val userUuid = userId.toUUID()
        val partnerUuid = partnerId.toUUID()

        PartnerStaffTable.select {
            (PartnerStaffTable.user eq userUuid) and
                    (PartnerStaffTable.partner eq partnerUuid) and
                    (PartnerStaffTable.role eq UserRole.CASHIER) and
                    (PartnerStaffTable.isActive eq true)
        }.any()
    }

    suspend fun isPartnerOwner(userId: String, partnerId: String): Boolean = dbQuery {
        PartnersTable.select {
            (PartnersTable.id eq partnerId.toUUID()) and
                    (PartnersTable.owner eq userId.toUUID())
        }.count() > 0
    }

    suspend fun getManagers(partnerId: String): List<Employer> = dbQuery {
        val partnerUuid = partnerId.toUUID()

        PartnerStaffTable
            .innerJoin(UsersTable)
            .select {
                (PartnerStaffTable.partner eq partnerUuid) and
                        (PartnerStaffTable.role eq UserRole.PARTNER_MANAGER)
                .and(PartnerStaffTable.isActive eq true)
            }
            .map { row ->
                val firstName = row[UsersTable.firstName] ?: ""
                val lastName = row[UsersTable.lastName] ?: ""

                Employer(
                    id = row[PartnerStaffTable.id].value.toString(),
                    userId = row[UsersTable.id].value.toString(),
                    name = "$firstName $lastName".trim(),
                    phone = row[UsersTable.phoneNumber],
                    active = row[PartnerStaffTable.isActive],
                    tradingPointId = null,
                    pointName = null
                )
            }
    }

    suspend fun addManager(userId: String, partnerId: String) = dbQuery {
        val userUuid = userId.toUUID()
        val partnerUuid = partnerId.toUUID()

        val existingEntry = PartnerStaffTable.select {
            (PartnerStaffTable.user eq userUuid) and
                    (PartnerStaffTable.partner eq partnerUuid) and
                    (PartnerStaffTable.role eq UserRole.PARTNER_MANAGER)
        }.singleOrNull()

        if (existingEntry != null) {
            if (!existingEntry[PartnerStaffTable.isActive]){
                throw LoyaltyException(AppErrorCode.WAS_FIRED, "Manager was fired")
            } else {
                throw LoyaltyException(AppErrorCode.ALREADY_JOINED, "Already a manager")
            }
        } else {
            PartnerStaffTable.insert {
                it[user] = userUuid
                it[partner] = partnerUuid
                it[role] = UserRole.PARTNER_MANAGER
                it[isActive] = true
                it[updatedAt] = nowUtc()
                it[tradingPoint] = null
            }
        }
    }

    suspend fun addCashier(userId: String, pointId: String, partnerId: String) = dbQuery {
        val userUuid = userId.toUUID()
        val pointUuid = pointId.toUUID()
        val partnerUuid = partnerId.toUUID()

        val existingEntry = PartnerStaffTable.select {
            (PartnerStaffTable.user eq userUuid) and
                    (PartnerStaffTable.partner eq partnerUuid) and
                    (PartnerStaffTable.tradingPoint eq pointUuid) and
                    (PartnerStaffTable.role eq UserRole.CASHIER)
        }.singleOrNull()

        if (existingEntry != null) {
            if (!existingEntry[PartnerStaffTable.isActive]){
                throw LoyaltyException(AppErrorCode.WAS_FIRED, "Cassier was fired")
            } else {
                throw LoyaltyException(AppErrorCode.ALREADY_JOINED, "Already a cassier")
            }
        } else {
            PartnerStaffTable.insert {
                it[user] = userUuid
                it[partner] = partnerUuid
                it[tradingPoint] = pointUuid
                it[role] = UserRole.CASHIER
                it[isActive] = true
                it[updatedAt] = nowUtc()
                it[canRefund] = false
            }
        }
    }

    suspend fun removeStaffMember(
        requesterUserId: String,
        partnerId: String,
        targetUserId: String,
        targetRole: UserRole,
        targetPointId: String? = null
    ) = dbQuery {
        val requesterUuid = requesterUserId.toUUID()
        val partnerUuid = partnerId.toUUID()
        val targetUserUuid = targetUserId.toUUID()

        val isOwner = PartnersTable.select {
            (PartnersTable.id eq partnerUuid) and (PartnersTable.owner eq requesterUuid)
        }.count() > 0

        val isManager = if (!isOwner) {
            PartnerStaffTable.select {
                (PartnerStaffTable.user eq requesterUuid) and
                        (PartnerStaffTable.partner eq partnerUuid) and
                        (PartnerStaffTable.role eq UserRole.PARTNER_MANAGER) and
                        (PartnerStaffTable.isActive eq true)
            }.count() > 0
        } else false

        if (!isOwner && !isManager) {
            throw LoyaltyException(AppErrorCode.FORBIDDEN, "You have no rights to manage staff")
        }

        if (isManager && targetRole == UserRole.PARTNER_MANAGER) {
            throw LoyaltyException(AppErrorCode.FORBIDDEN, "Managers cannot remove other managers. Only the Owner can do this.")
        }

        val query = PartnerStaffTable.select {
            (PartnerStaffTable.user eq targetUserUuid) and
                    (PartnerStaffTable.partner eq partnerUuid) and
                    (PartnerStaffTable.role eq targetRole) and
                    (PartnerStaffTable.isActive eq true)
        }

        if (targetRole == UserRole.CASHIER) {
            if (targetPointId == null) throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Point ID required to remove a cashier")
            query.andWhere { PartnerStaffTable.tradingPoint eq targetPointId.toUUID() }
        }

        val targetRecordId = query.singleOrNull()?.get(PartnerStaffTable.id)
            ?: throw LoyaltyException(AppErrorCode.USER_NOT_FOUND, "Active staff member not found with these parameters")

        PartnerStaffTable.update({ PartnerStaffTable.id eq targetRecordId }) {
            it[isActive] = false
            it[updatedAt] = nowUtc()
        }
    }
    
    suspend fun isUserCashierAtPoint(userId: String, pointId: String): Boolean = dbQuery {
        val userUuid = userId.toUUID()
        val pointUuid = pointId.toUUID()

        val partnerId = TradingPointsTable.slice(TradingPointsTable.partner)
            .select { TradingPointsTable.id eq pointUuid }.singleOrNull()
            ?.get(TradingPointsTable.partner)?.value ?: return@dbQuery false

        PartnerStaffTable.select {
            (PartnerStaffTable.user eq userUuid) and (PartnerStaffTable.partner eq partnerId) and (PartnerStaffTable.isActive eq true)
        }.any { row ->
            val role = row[PartnerStaffTable.role]
            val assignedPoint = row[PartnerStaffTable.tradingPoint]?.value
            when (role) {
                UserRole.CASHIER -> assignedPoint == pointUuid
                else -> false
            }
        }
    }
}

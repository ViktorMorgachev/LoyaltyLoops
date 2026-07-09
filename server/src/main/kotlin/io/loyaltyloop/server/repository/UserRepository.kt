package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.DeviceTokensTable
import io.loyaltyloop.server.database.tables.PartnerStaffTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUserDto
import io.loyaltyloop.server.utils.toUtcLocalDateTime
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.PartnerStatus
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

// TODO checked
class UserRepository {

    suspend fun createUser(dto: UserDto): String = dbQuery {
        if (dto.qrSecret.isBlank()) {
            throw LoyaltyException(AppErrorCode.SECURITY_QR_SECRET_MISSING, "QR Secret missing")
        }
        val newId = UsersTable.insertAndGetId {
            it[phoneNumber] = dto.phoneNumber
            it[countryCode] = dto.countryCode
            it[language] = dto.language
            it[qrSecret] = dto.qrSecret
            it[frozenUntil] = null
            it[telegramId] = dto.telegramId
            it[isDeleted] = false
            it[firstName] = dto.firstName
            it[lastName] = dto.lastName
            it[email] = dto.email
            it[updatedAt] = nowUtc()
        }.value.toString()

        if (newId.isEmpty()){
            throw LoyaltyException(AppErrorCode.USER_CREATION_FAILED, "DB Error")
        }
        newId
    }

    suspend fun updateUserProfile(userId: String, request: UpdateProfileRequest, newLanguage: String? = null) = dbQuery {
        val uuid = userId.toUUID()

        UsersTable.update({ UsersTable.id eq uuid }) {
            it[firstName] = request.firstName
            it[lastName] = request.lastName
            it[email] = request.email
            if (newLanguage != null) {
                it[language] = newLanguage
            }
            it[this.updatedAt] = nowUtc()
        }
    }

    suspend fun restoreUser(userId: String) = dbQuery {
        val uuid = userId.toUUID()
        UsersTable.update({ UsersTable.id eq uuid }) {
            it[isDeleted] = false
            it[deletionReason] = null
            it[frozenUntil] = null
            it[this.updatedAt] = nowUtc()
        }
    }

    suspend fun updateUserLanguage(userId: String, newLanguage: String) = dbQuery {
        val uuid = userId.toUUID()
        UsersTable.update({ UsersTable.id eq uuid }) {
            it[language] = newLanguage
            it[this.updatedAt] = nowUtc()
        }
    }

    suspend fun getUserByTelegramId(tgId: Long): UserDto? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.telegramId eq tgId }
            .map { it.toUserDto() }
            .singleOrNull()
    }

    suspend fun linkTelegram(userId: String, tgId: Long) = dbQuery {
        val userUuid = userId.toUUID()
        UsersTable.update({ UsersTable.id eq userUuid }) {
            it[telegramId] = tgId
            it[this.updatedAt] = nowUtc()
        }
    }

    suspend fun getUserByPhone(phone: String): UserDto? = dbQuery {
        UsersTable.selectAll()
            .where { UsersTable.phoneNumber eq phone }
            .map { it.toUserDto() }
            .singleOrNull()
    }

    suspend fun getUserById(userId: String): UserDto? = dbQuery {
        val uuid = userId.toUUID()

        UsersTable.selectAll()
            .where { UsersTable.id eq uuid }
            .map { it.toUserDto() }
            .singleOrNull()
    }

    // --- ADMIN / ROLES ---
    suspend fun setFrozenUntil(userId: String, timestamp: Long) = dbQuery {
        val userUuid = userId.toUUID()
        val frozenDate = timestamp.toUtcLocalDateTime()

        UsersTable.update({ UsersTable.id eq userUuid }) {
            it[frozenUntil] = frozenDate
            it[this.updatedAt] = nowUtc()
        }
    }

    suspend fun getFrozenUntil(userId: String): Long? = dbQuery {
        val userUuid = userId.toUUID()

        val frozenDate = UsersTable
            .slice(UsersTable.frozenUntil)
            .select { UsersTable.id eq userUuid }
            .singleOrNull()
            ?.get(UsersTable.frozenUntil)

        frozenDate?.toUtcMillis()
    }

    suspend fun getUserWorkspaces(userId: String): List<UserWorkspace> = dbQuery {
        val workspaces = mutableListOf<UserWorkspace>()
        val userUuid = userId.toUUID()

        val sysRole = SystemStaffTable
            .slice(SystemStaffTable.role)
            .select {
                (SystemStaffTable.user eq userUuid) and
                        (SystemStaffTable.isActive eq true)
            }
            .singleOrNull()
            ?.get(SystemStaffTable.role)

        if (sysRole != null) {
            workspaces.add(
                UserWorkspace(
                    id = "platform",
                    title ="LoyaltyLoop",
                    role = sysRole,
                    requirePin = true
                )
            )
        }

        PartnersTable
            .slice(PartnersTable.id, PartnersTable.businessName)
            .select { PartnersTable.owner eq userUuid }
            .forEach { row ->
                workspaces.add(
                    UserWorkspace(
                        id = row[PartnersTable.id].value.toString(),
                        title = row[PartnersTable.businessName],
                        role = UserRole.PARTNER_ADMIN,
                        requirePin = true
                    )
                )
            }

        PartnerStaffTable
            .innerJoin(PartnersTable)
            .join(TradingPointsTable, JoinType.LEFT, PartnerStaffTable.tradingPoint, TradingPointsTable.id)
            .slice(
                PartnerStaffTable.role,
                PartnerStaffTable.tradingPoint,
                PartnersTable.id,
                PartnersTable.businessName,
                TradingPointsTable.id,
                TradingPointsTable.name
            )
            .select {
                (PartnerStaffTable.user eq userUuid) and
                        (PartnerStaffTable.isActive eq true)
            }
            .forEach { row ->
                val role = row[PartnerStaffTable.role]
                val partnerName = row[PartnersTable.businessName]
                val partnerId = row[PartnersTable.id].value.toString()

                when (role) {
                    UserRole.PARTNER_MANAGER -> {
                        workspaces.add(
                            UserWorkspace(
                                id = partnerId,
                                title = "$partnerName manager",
                                role = role,
                                requirePin = true
                            )
                        )
                    }

                    UserRole.CASHIER -> {
                        val pointId = row.getOrNull(TradingPointsTable.id)?.value?.toString()
                        val pointName = row.getOrNull(TradingPointsTable.name)

                        if (pointId != null && pointName != null) {
                            workspaces.add(
                                UserWorkspace(
                                    id = pointId,
                                    title = "$partnerName — $pointName",
                                    role = role,
                                    requirePin = true
                                )
                            )
                        }
                    }

                    else -> {}
                }
            }

        workspaces
    }


    suspend fun deleteUser(userId: String, reason: String = "Deleted by request"): Boolean = dbQuery {
        val userUuid = userId.toUUID()

        val updatedAt = nowUtc()

        RefreshTokensTable.deleteWhere {
            RefreshTokensTable.user eq userUuid
        }

        DeviceTokensTable.deleteWhere {
            DeviceTokensTable.user eq userUuid
        }

        SystemStaffTable.update({ SystemStaffTable.user eq userUuid }) {
            it[isActive] = false
            it[this.updatedAt] = updatedAt
        }

        PartnerStaffTable.update({ PartnerStaffTable.user eq userUuid }) {
            it[isActive] = false
            it[this.updatedAt] = updatedAt
        }

        val updatedCount = UsersTable.update({ UsersTable.id eq userUuid }) {
            it[isDeleted] = true
            it[deletionReason] = reason
            it[this.updatedAt] = updatedAt
            it[frozenUntil] = null
        }

        updatedCount > 0
    }

    suspend fun isBusinessOwner(userId: String): Boolean = dbQuery {
        val userUuid = userId.toUUID()
        PartnersTable
            .select {
                (PartnersTable.owner eq userUuid) and
                        (PartnersTable.status neq PartnerStatus.BLOCKED)
            }
            .count() > 0
    }
}

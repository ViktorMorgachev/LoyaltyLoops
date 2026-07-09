package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PlatformInvitesTable
import io.loyaltyloop.server.database.tables.PlatformInvitesTable.expiresAt
import io.loyaltyloop.server.database.tables.PlatformInvitesTable.isUsed
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.Employer
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID
import kotlin.collections.get

// TODO checked
class SystemStaffRepository {

    // ==========================================
    // 2. УПРАВЛЕНИЕ ПЕРСОНАЛОМ (STAFF MANAGEMENT)
    // ==========================================

    suspend fun checkForRolePermission(role: UserRole) {
        val allowedRoles = listOf(
            UserRole.PLATFORM_SUPER_MANAGER,
            UserRole.PLATFORM_MANAGER,
            UserRole.PLATFORM_SUPER_ADMIN
        )

        if (role !in allowedRoles) {
            throw LoyaltyException(
                AppErrorCode.INVALID_REQUEST,
                "Role $role cannot be added to System Staff table. Use PartnerStaffTable instead."
            )
        }
    }

    suspend fun checkExistingRole(userUuid: UUID) {

        val existingEntry = SystemStaffTable
            .select { SystemStaffTable.user eq userUuid }
            .singleOrNull()

        if (existingEntry != null) {
            val isActive = existingEntry[SystemStaffTable.isActive]

            if (isActive) {
                throw LoyaltyException(
                    AppErrorCode.ALREADY_JOINED,
                    "User already has a platform role"
                )
            } else {
                throw LoyaltyException(
                    AppErrorCode.WAS_FIRED,
                    "User was fired from  platform role"
                )
            }
        }
    }

    suspend fun createSystemStaff(
        userId: String,
        role: UserRole,
        defaultPinHash: String? = null
    ) = dbQuery {
        val userUuid = userId.toUUID()
        checkForRolePermission(role = role)
        checkExistingRole(userUuid)

        SystemStaffTable.insert {
            it[user] = userUuid
            it[this.role] = role
            it[this.pinHash] = defaultPinHash
            it[this.isActive] = true
        }
    }


    suspend fun restoreSystemStaff(
        userId: String,
        role: UserRole,
        defaultPinHash: String? = null
    ) = dbQuery {
        val userUuid = userId.toUUID()

        val existingEntry = SystemStaffTable
            .select { SystemStaffTable.user eq userUuid }
            .singleOrNull()

        if (existingEntry != null) {
            SystemStaffTable.update({ SystemStaffTable.id eq existingEntry[SystemStaffTable.id] }) {
                it[this.isActive] = true
                it[this.role] = role // Обновляем роль (вдруг повысили)
                if (defaultPinHash != null) {
                    it[this.pinHash] = defaultPinHash
                }
                it[updatedAt] = nowUtc()
            }
        }

    }

    suspend fun removeSystemStaff(staffId: String, currentUserRole: UserRole) = dbQuery {

        val targetStaffRole = getSystemRoleByStaffId(staffId)
        if (targetStaffRole != null) {
            if (targetStaffRole == UserRole.PLATFORM_SUPER_ADMIN) {
                throw LoyaltyException(AppErrorCode.FORBIDDEN, "Cannot delete Super Admin")
            }
            if (currentUserRole == UserRole.PLATFORM_SUPER_MANAGER && targetStaffRole == UserRole.PLATFORM_SUPER_MANAGER) {
                throw LoyaltyException(AppErrorCode.FORBIDDEN, "Cannot delete another Super Manager")
            }
        }

        SystemStaffTable.update({ SystemStaffTable.id eq UUID.fromString(staffId) }) {
            it[isActive] = false
            it[updatedAt] = nowUtc()
        }
    }

    // ==========================================
    // 1. УПРАВЛЕНИЕ ИНВАЙТАМИ (INVITES)
    // ==========================================
    suspend fun generateInvite(targetRole: UserRole, creatorUserId: String): String = dbQuery {

        val creatorUuid = creatorUserId.toUUID()

        val role = getSystemRole(creatorUserId)

        val canInvite = when (role) {
            UserRole.PLATFORM_SUPER_ADMIN -> targetRole == UserRole.PLATFORM_SUPER_MANAGER || targetRole == UserRole.PLATFORM_MANAGER
            UserRole.PLATFORM_SUPER_MANAGER -> targetRole == UserRole.PLATFORM_MANAGER
            else -> false
        }

        if (!canInvite) throw LoyaltyException(AppErrorCode.FORBIDDEN, "You cannot invite this role")


        val creatorStaffId = SystemStaffTable
            .slice(SystemStaffTable.id)
            .select { SystemStaffTable.user eq creatorUuid }
            .singleOrNull()
            ?.get(SystemStaffTable.id)
            ?: throw LoyaltyException(AppErrorCode.FORBIDDEN, "Not a staff member")

        // Генерируем код (можно использовать твой рандом или буквенно-цифровой)
        val inviteCode = "ST-" + (100000..999999).random()

        // Устанавливаем срок жизни (например, 24 часа)
        val now = nowUtc()
        val expirationDate = now.plusHours(24)

        PlatformInvitesTable.insert {
            it[code] = inviteCode
            it[this.role] = targetRole
            it[createdBy] = creatorStaffId
            it[expiresAt] = expirationDate
            it[isUsed] = false
        }

        inviteCode
    }

    suspend fun validateInvite(code: String): UserRole = dbQuery {
        val now = nowUtc()

        PlatformInvitesTable
            .slice(PlatformInvitesTable.role)
            .select {
                (PlatformInvitesTable.code eq code) and       // Код совпадает
                        (isUsed eq false) and    // Еще не использован
                        (expiresAt greater now)  // Срок не вышел
            }
            .singleOrNull()
            ?.get(PlatformInvitesTable.role) ?:  throw LoyaltyException(AppErrorCode.INVALID_INVITE_CODE, "Invalid Invite Code") // Exposed сам вернет UserRole (Enum)
    }

    suspend fun acceptInvite(
        code: String,
        userId: String,
        role: UserRole,
        defaultPinHash: String? = null,
    ) = dbQuery {

        val now = nowUtc()
        val userUuid = userId.toUUID()
        checkForRolePermission(role = role)
        checkExistingRole(userUuid)

        SystemStaffTable.insert {
            it[user] = userUuid
            it[this.role] = role
            it[this.pinHash] = defaultPinHash
            it[this.isActive] = true
        }


        val updated = PlatformInvitesTable.update({ PlatformInvitesTable.code eq code }) {
            it[isUsed] = true
            it[usedAt] = now
            it[usedBy] = userUuid
        }

        if (updated == 0) {
            throw LoyaltyException(
                AppErrorCode.INVALID_INVITE_CODE,
                "Invite expired or already used"
            )
        }
    }

    // ==========================================
// 3. ПРОВЕРКИ ПРАВ (READ)
// ==========================================
    suspend fun getSystemRole(userId: String): UserRole = dbQuery {
        SystemStaffTable
            .slice(SystemStaffTable.role)
            .select {
                (SystemStaffTable.user eq userId.toUUID()) and
                        (SystemStaffTable.isActive eq true)
            }
            .singleOrNull()
            ?.get(SystemStaffTable.role) ?:  throw LoyaltyException(AppErrorCode.INTERNAL_ERROR, "Role in SystemStaffTable not found by user: $userId")
    }

    suspend fun getSystemRoleByStaffId(staffId: String): UserRole? = dbQuery {
        val staffUuid = staffId.toUUID()
        SystemStaffTable
            .slice(SystemStaffTable.role)
            .select { SystemStaffTable.id eq staffUuid }
            .singleOrNull()
            ?.get(SystemStaffTable.role)
    }


    suspend fun getSystemStaff(role: UserRole? = null): List<Employer> = dbQuery {
        val query = SystemStaffTable.innerJoin(UsersTable).selectAll()

        if (role != null) {
            query.andWhere { SystemStaffTable.role eq role }
        }
        query.orderBy(SystemStaffTable.isActive to SortOrder.DESC)
            .map { row ->
                val firstName = row[UsersTable.firstName] ?: ""
                val lastName = row[UsersTable.lastName] ?: ""
                Employer(
                    id = row[SystemStaffTable.id].value.toString(),
                    userId = row[UsersTable.id].value.toString(),
                    name = "$firstName $lastName".trim(),
                    phone = row[UsersTable.phoneNumber],
                    active = row[SystemStaffTable.isActive],
                    pointName = row[SystemStaffTable.role].name
                )
            }
    }
}

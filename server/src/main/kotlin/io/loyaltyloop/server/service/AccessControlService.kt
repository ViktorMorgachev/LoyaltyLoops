package io.loyaltyloop.server.service

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.repository.PartnerStaffRepository
import io.loyaltyloop.server.repository.SystemStaffRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.select
import java.util.UUID

// TODO checked
class AccessControlService(
    private val systemStaffRepository: SystemStaffRepository,
    private val partnerStaffRepository: PartnerStaffRepository,
) {
    suspend fun requireSystemRole(userId: String,  vararg allowedRoles: UserRole) {
        val role = systemStaffRepository.getSystemRole(userId)

        if (allowedRoles.isNotEmpty() && role !in allowedRoles) {
            throw LoyaltyException(AppErrorCode.FORBIDDEN, "Insufficient permissions. Required: ${allowedRoles.joinToString()}")
        }
    }

    /**
     * Проверяет доступ к ресурсам Партнера (настройки, сотрудники, статистика).
     * @param allowManager - разрешить ли доступ Менеджерам? (По умолчанию false - только Владелец)
     */
    suspend fun requirePartnerAccess(
        userId: String,
        partnerId: String,
        allowManager: Boolean = false
    ) {
        // 1. Проверка Владельца (Самая частая)
        if (partnerStaffRepository.isPartnerOwner(userId, partnerId)) return

        // 2. Проверка Менеджера (если разрешено)
        if (allowManager) {
            if (partnerStaffRepository.isHasManagerOfPartner(userId, partnerId)) return
        }

       if (systemStaffRepository.getSystemRole(userId) == UserRole.PLATFORM_SUPER_ADMIN) return

        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied to partner $partnerId")
    }

    /**
     * Проверяет доступ к конкретной Точке.
     * Сначала находит Партнера точки, потом проверяет роли.
     * @param allowManager - пускать Менеджеров?
     * @param allowCashier - пускать Кассиров (именно этой точки)?
     */
    suspend fun requirePointAccess(
        userId: String,
        pointId: String,
    ) {
        if (partnerStaffRepository.isUserCashierAtPoint(userId, pointId)) return

        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied to point $pointId")
    }

    suspend fun hasUser(userUuid: UUID): Boolean = dbQuery {
        UsersTable.select { UsersTable.id eq userUuid }.count() > 0
    }

    suspend fun isDeleted(userUuid: UUID): Boolean = dbQuery {
       UsersTable
            .slice(UsersTable.isDeleted)
            .select { UsersTable.id eq userUuid }
            .singleOrNull()
            ?.get(UsersTable.isDeleted)!!
    }

    suspend fun isAccountFrozen(userUuid: UUID): Boolean = dbQuery {
        val now = nowUtc().toUtcMillis()

        val frozenUntil = UsersTable
            .slice(UsersTable.frozenUntil)
            .select { UsersTable.id eq userUuid }
            .singleOrNull()
            ?.get(UsersTable.frozenUntil)
            ?.toUtcMillis()

        frozenUntil != null && frozenUntil > now
    }
}

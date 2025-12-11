package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.*
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.toBaseCardDto
import io.loyaltyloop.shared.models.*
import io.loyaltyloop.server.utils.toUserDto
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class UserRepository {

    // --- USER MANAGEMENT ---

    suspend fun createUser(dto: UserDto) = dbQuery {
        if (dto.qrSecret.isBlank()) throw LoyaltyException(AppErrorCode.SECURITY_QR_SECRET_MISSING, "QR Secret missing")
        UsersTable.insert {
            it[id] = dto.id.ifEmpty { UUID.randomUUID().toString() }
            it[phoneNumber] = dto.phoneNumber
            it[countryCode] = dto.countryCode
            it[createdAt] = System.currentTimeMillis()
            it[language] = dto.language
            it[qrSecret] = dto.qrSecret
            it[frozenUntil] = dto.isFrozenUntil
            it[isDeleted] = false
        }
    }

    suspend fun updateUserProfile(userDto: UserDto, lang: String?, request: UpdateProfileRequest) = dbQuery {
        UsersTable.update({ UsersTable.id eq userDto.id }) {
            it[firstName] = request.firstName
            it[lastName] = request.lastName
            it[email] = request.email
            it[qrSecret] = userDto.qrSecret
            lang?.let { l -> it[language] = l }
        }
    }

    suspend fun updateUserLanguage(userId: String, newLanguage: String) = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) { it[language] = newLanguage }
    }

    // --- GETTERS ---

    suspend fun getUserByPhone(phone: String): UserDto? = dbQuery {
        // Фильтруем удаленных
        UsersTable.selectAll().where { (UsersTable.phoneNumber eq phone) and (UsersTable.isDeleted eq false) }
            .map { it.toUserDto() }
            .singleOrNull()
    }

    suspend fun getUserById(userId: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { (UsersTable.id eq userId) and (UsersTable.isDeleted eq false) }
            .map { it.toUserDto() }
            .singleOrNull()
    }

    // Метод полезен для восстановления аккаунтов или проверки истории
    suspend fun getDeletedUserByPhone(phone: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { (UsersTable.phoneNumber eq phone) and (UsersTable.isDeleted eq true) }
            .map { it.toUserDto() }
            .singleOrNull()
    }

    suspend fun userExists(userId: String): Boolean = dbQuery {
        !UsersTable.select { UsersTable.id eq userId }.empty()
    }

    suspend fun getAllUsers(): List<UserDto> = dbQuery {
        UsersTable.selectAll().map { it.toUserDto() }
    }

    // --- ADMIN / ROLES ---

    // Дублирует логику deleteUser, но иногда полезно для админки (указать причину бана)
    suspend fun markUserDeleted(userId: String, reason: String) = dbQuery {
        // Важно: при бане тоже надо убивать токены
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }

        UsersTable.update({ UsersTable.id eq userId }) {
            it[isDeleted] = true
            it[deletionReason] = reason
        }
    }

    suspend fun setFrozenUntil(userId: String, timestamp: Long?) = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[frozenUntil] = timestamp
        }
    }

    suspend fun getFrozenUntil(userId: String): Long? = dbQuery {
        UsersTable.slice(UsersTable.frozenUntil)
            .select { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.frozenUntil)
    }



    // --- CARDS & LOYALTY ---

    suspend fun findOrCreateCard(
        userId: String,
        partnerId: String,
        partnerName: String,
        partnerColor: String,
        partnerLogo: String?,
        defaultVisitsTarget: Int
    ): Pair<LoyaltyCardDto, Boolean> = dbQuery {
        // 1. Ищем существующую карту
        val existingRow = LoyaltyCardTable.selectAll()
            .where { (LoyaltyCardTable.userId eq userId) and (LoyaltyCardTable.partnerId eq partnerId) }
            .limit(1)
            .singleOrNull()

        if (existingRow != null) {
            val dto = existingRow.toBaseCardDto().copy(
                partnerName = partnerName,
                cardColor = partnerColor,
                logoUrl = partnerLogo,
                visitsTarget = defaultVisitsTarget
            )
            return@dbQuery dto to false
        }

        // 2. Создаем новую
        val newId = UUID.randomUUID().toString()
        LoyaltyCardTable.insert {
            it[id] = newId
            it[this.userId] = userId
            it[this.partnerId] = partnerId
            it[balance] = 0.0
            it[totalSpent] = 0.0
            it[tierLevel] = 1
            it[visitsCount] = 0
            it[trustScore] = 4.0
            it[fraudFlag] = false
        }

        val newCard = LoyaltyCardDto(
            id = newId,
            userId = userId,
            partnerId = partnerId,
            balance = 0.0,
            totalSpent = 0.0,
            tierLevel = 1,
            block = null,
            pause = null,
            visitsTarget = defaultVisitsTarget,
            partnerName = partnerName,
            cardColor = partnerColor,
            logoUrl = partnerLogo,
            visitsCount = 0,
            trustScore = 4.0,
            fraudFlag = false,
            riskLevel = RiskLevel.YELLOW
        )
        newCard to true
    }

    suspend fun getUserCards(userId: String): List<LoyaltyCardDto> = dbQuery {
        // Здесь нужен явный JOIN, так как в LoyaltyCardTable нет references
        LoyaltyCardTable
            .join(PartnersTable, JoinType.INNER, LoyaltyCardTable.partnerId, PartnersTable.id)
            .selectAll().where { LoyaltyCardTable.userId eq userId }
            .map { row ->
                row.toBaseCardDto().copy(
                    partnerName = row[PartnersTable.businessName],
                    cardColor = row[PartnersTable.color],
                    logoUrl = row[PartnersTable.logoUrl],
                    visitsTarget = row[PartnersTable.defaultVisitsTarget]
                )
            }
    }

    // --- WORKSPACES & JOBS ---

    // DRY/KISS VIOLATION:
    // Логика этого метода (поиск точек кассира) полностью дублируется внутри метода getUserWorkspaces (блок 3).
    // Решение: Метод getUserWorkspaces может вызывать getCashierJobs и мапить результат в Workspace,
    // чтобы не писать один и тот же SQL Join дважды.
    suspend fun getCashierJobs(userId: String): List<CashierJobEntity> = dbQuery {
        PartnerCashiersTable
            .innerJoin(TradingPointsTable)
            .innerJoin(PartnersTable)
            .selectAll().where { PartnerCashiersTable.userId eq userId }
            .map {
                CashierJobEntity(
                    tradingPointId = it[TradingPointsTable.id],
                    pointName = it[TradingPointsTable.name],
                    businessName = it[PartnersTable.businessName]
                )
            }
    }

    suspend fun getUserWorkspaces(userId: String): List<UserWorkspace> = dbQuery {
        val workspaces = mutableListOf<UserWorkspace>()

        // 1. Platform Staff
        val staffRole = SystemStaffTable.innerJoin(UsersTable)
            .slice(SystemStaffTable.role)
            .select { SystemStaffTable.userId eq userId }
            .singleOrNull()
            ?.get(SystemStaffTable.role)

        if (staffRole != null) {
            workspaces.add(UserWorkspace("platform_${staffRole}", "Platform", staffRole, false))
        }

        // 2. Owner
        PartnersTable.slice(PartnersTable.id, PartnersTable.businessName)
            .select { PartnersTable.ownerId eq userId }
            .forEach {
                workspaces.add(UserWorkspace(it[PartnersTable.id], it[PartnersTable.businessName], UserRole.PARTNER_ADMIN, true))
            }

        // 3. Cashier
        // Здесь дублируется логика getCashierJobs.
        // Если исправлять DRY, можно вызвать getCashierJobs(userId) и сделать цикл по результатам.
        // Оставляю SQL для производительности (меньше маппинга промежуточных объектов), но имейте в виду дублирование.
        PartnerCashiersTable
            .innerJoin(TradingPointsTable)
            .join(
                otherTable = PartnersTable,
                joinType = JoinType.INNER,
                onColumn = TradingPointsTable.partnerId, // Явно говорим: бери партнера из Точки
                otherColumn = PartnersTable.id
            )
            .slice(TradingPointsTable.id, TradingPointsTable.name, PartnersTable.businessName)
            .select { PartnerCashiersTable.userId eq userId }
            .forEach {
                workspaces.add(UserWorkspace(it[TradingPointsTable.id], "${it[PartnersTable.businessName]} (${it[TradingPointsTable.name]})", UserRole.CASHIER, false))
            }


        // 4. Manager
        PartnerManagersTable
            .innerJoin(PartnersTable)
            .slice(PartnersTable.id, PartnersTable.businessName)
            .select { PartnerManagersTable.userId eq userId }
            .forEach {
                workspaces.add(UserWorkspace(it[PartnersTable.id], "${it[PartnersTable.businessName]} (Manager)", UserRole.PARTNER_MANAGER, true))
            }

        workspaces
    }

    // --- SYSTEM & TOKENS ---

    suspend fun saveRefreshToken(token: String, userId: String, expiresAt: Long) = dbQuery {
        RefreshTokensTable.insert {
            it[this.token] = token
            it[this.userId] = userId
            it[this.expiresAt] = expiresAt
        }
    }

    suspend fun findRefreshToken(token: String): String? = dbQuery {
        RefreshTokensTable.slice(RefreshTokensTable.userId)
            .select { RefreshTokensTable.token eq token }
            .singleOrNull()
            ?.get(RefreshTokensTable.userId)
    }

    suspend fun deleteRefreshToken(token: String) = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.token eq token }
    }

    suspend fun deleteAllTokensForUser(userId: String) = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
    }

    // --- DELETE (SOFT DELETE) ---

    suspend fun deleteUser(userId: String, reason: String = "Deleted by request"): Boolean = dbQuery {
        // 1. Hard Delete Токенов (Обязательно для безопасности)
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }

        // 2. Soft Delete Пользователя
        val updated = UsersTable.update({ UsersTable.id eq userId }) {
            it[isDeleted] = true
            it[deletionReason] = reason
            it[frozenUntil] = null // Снимаем заморозку, так как аккаунт удален
        }

        // Примечание: Мы НЕ удаляем записи из SystemStaff, PartnerManagers и т.д.
        // Так как пользователь помечен isDeleted=true, методы авторизации (getUserByPhone) его не найдут.
        // Но для целостности БД лучше бы удалять Staff роли. Оставим пока так для сохранения истории.

        updated > 0
    }
}
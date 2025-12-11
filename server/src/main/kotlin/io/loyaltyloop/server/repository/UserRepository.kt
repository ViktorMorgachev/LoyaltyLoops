package io.loyaltyloop.server.repository

import org.jetbrains.exposed.sql.selectAll
import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnerCashiersTable
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.database.tables.SystemStaffTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.PartnerManagersTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.CashierJobEntity
import io.loyaltyloop.shared.models.CardBlockStatus
import io.loyaltyloop.shared.models.CardPauseStatus
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.PartnerEntity
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update
import java.util.UUID

class UserRepository {

    // Добавить пользователя
    suspend fun createUser(dto: UserDto) = dbQuery {

        if (dto.qrSecret.isBlank()) {
            throw LoyaltyException(AppErrorCode.SECURITY_QR_SECRET_MISSING, "SECURITY ERROR: QR Secret missing")
        }
        UsersTable.insert {
            it[id] = dto.id.ifEmpty { UUID.randomUUID().toString() } // Генерируем ID если нет
            it[phoneNumber] = dto.phoneNumber
            it[countryCode] = dto.countryCode
            it[createdAt] = System.currentTimeMillis()
            it[language] = dto.language
            it[qrSecret] = dto.qrSecret
            it[frozenUntil] = dto.isFrozenUntil
        }
    }

    // Обновление профиля
    suspend fun updateUserProfile(userDto: UserDto, lang: String?, request: UpdateProfileRequest) =
        dbQuery {
            UsersTable.update({ UsersTable.id eq userDto.id }) { dto ->
                dto[firstName] = request.firstName
                dto[lastName] = request.lastName
                dto[email] = request.email
                dto[qrSecret] = userDto.qrSecret
                lang?.let {
                    dto[language] = it
                }
            }
        }

    // Обновляем язык пользователя, если он изменился
    suspend fun updateUserLanguage(userId: String, newLanguage: String) = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[language] = newLanguage
        }
    }

    // Получить всех (для теста)
    suspend fun getAllUsers(): List<UserDto> = dbQuery {
        UsersTable.selectAll().map { rowToUserDto(it) }
    }

    suspend fun getUserByPhone(phone: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { (UsersTable.phoneNumber eq phone) and (UsersTable.isDeleted eq false) }
            .map { rowToUserDto(it) }
            .singleOrNull()
    }

    suspend fun getDeletedUserByPhone(phone: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { (UsersTable.phoneNumber eq phone) and (UsersTable.isDeleted eq true) }
            .map { rowToUserDto(it) }
            .singleOrNull()
    }

    suspend fun getUserById(userId: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { (UsersTable.id eq userId) and (UsersTable.isDeleted eq false) }
            .map { rowToUserDto(it) }
            .singleOrNull()
    }

    suspend fun userExists(userId: String): Boolean = dbQuery {
        !UsersTable.selectAll().where { UsersTable.id eq userId }.empty()
    }

    // Вспомогательная функция: превращает строку БД в класс Kotlin
    private fun rowToUserDto(row: ResultRow): UserDto {
        return UserDto(
            id = row[UsersTable.id],
            phoneNumber = row[UsersTable.phoneNumber],
            countryCode = row[UsersTable.countryCode],
            firstName = row[UsersTable.firstName],
            lastName = row[UsersTable.lastName],
            email = row[UsersTable.email],
            qrSecret = row[UsersTable.qrSecret],
            language = row[UsersTable.language],
            isFrozenUntil = row[UsersTable.frozenUntil],
            isDeleted = row[UsersTable.isDeleted]
        )
    }

    suspend fun markUserDeleted(userId: String, reason: String) = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[isDeleted] = true
            it[deletionReason] = reason
        }
    }

    suspend fun setSuperAdmin(userId: String, isAdmin: Boolean) = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[isSuperAdmin] = isAdmin
        }
    }

    suspend fun setFrozenUntil(userId: String, timestamp: Long?) = dbQuery {
        UsersTable.update({ UsersTable.id eq userId }) {
            it[frozenUntil] = timestamp
        }
    }

    suspend fun getFrozenUntil(userId: String): Long? = dbQuery {
        UsersTable
            .slice(UsersTable.frozenUntil)
            .select { UsersTable.id eq userId }
            .singleOrNull()
            ?.get(UsersTable.frozenUntil)
    }

    suspend fun isSuperAdmin(userId: String): Boolean = dbQuery {
        UsersTable.selectAll().where { UsersTable.id eq userId }
            .map { it[UsersTable.isSuperAdmin] }
            .singleOrNull() == true
    }

    suspend fun hasAnyPlatformRole(userId: String): Boolean = dbQuery {
        !SystemStaffTable.select { SystemStaffTable.userId eq userId }.empty()
    }

    suspend fun hasPlatformRole(userId: String, role: UserRole): Boolean = dbQuery {
        !SystemStaffTable.select {
            (SystemStaffTable.userId eq userId) and (SystemStaffTable.role eq role)
        }.empty()
    }

    suspend fun findOrCreateCard(
        userId: String,
        partnerId: String,
        partnerName: String,
        partnerColor: String,
        partnerLogo: String?,
        defaultVisitsTarget: Int
    ): Pair<LoyaltyCardDto, Boolean> = dbQuery {

        // 1. Ищем существующую (устойчиво к дубликатам)
        val existingRow = LoyaltyCardTable.selectAll()
            .where { (LoyaltyCardTable.userId eq userId) and (LoyaltyCardTable.partnerId eq partnerId) }
            .limit(1) // Если вдруг дубликаты уже есть - берем первую
            .singleOrNull()

        if (existingRow != null) {
            // Для старой карты - подтягиваем данные через маппер (он там пытается делать join или берет переданные?)
            // Нюанс: rowToCardDto у нас заточен под JOIN.
            // Если мы тут делаем простой select без join, то rowToCardDto вернет пустые поля.
            // Давай сделаем умнее: вернем DTO, но перезапишем UI поля теми, что мы передали в функцию!
            val dto = rowToCardDto(existingRow).copy(
                partnerName = partnerName,
                cardColor = partnerColor,
                logoUrl = partnerLogo,
                visitsTarget = defaultVisitsTarget
            )
            return@dbQuery dto to false
        }

        // 2. Создаем новую
        val newId = java.util.UUID.randomUUID().toString()

        LoyaltyCardTable.insert {
            it[id] = newId
            it[this.userId] = userId
            it[this.partnerId] = partnerId
            it[balance] = 0.0
            it[totalSpent] = 0.0
            it[tierLevel] = 1
            it[this.visitsCount] = 0
            it[blockedUntil] = null
            it[blockedReason] = null
            it[isPaused] = false
            it[pauseReason] = null
        }

        // 3. Собираем полный DTO сразу
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
            // Используем переданные параметры
            partnerName = partnerName,
            cardColor = partnerColor,
            logoUrl = partnerLogo,
            visitsCount = 0
        )

        newCard to true
    }

    // 1. Обновленный маппер (теперь учитывает данные партнера)
    private fun rowToCardDto(row: ResultRow): LoyaltyCardDto {
        val block = row[LoyaltyCardTable.blockedUntil]?.let {
            CardBlockStatus(
                until = it,
                reason = row[LoyaltyCardTable.blockedReason]
            )
        }
        val pause = if (row[LoyaltyCardTable.isPaused]) {
            CardPauseStatus(reason = row[LoyaltyCardTable.pauseReason])
        } else {
            null
        }

        return LoyaltyCardDto(
            id = row[LoyaltyCardTable.id],
            userId = row[LoyaltyCardTable.userId],
            partnerId = row[LoyaltyCardTable.partnerId],
            balance = row[LoyaltyCardTable.balance],
            totalSpent = row[LoyaltyCardTable.totalSpent],
            tierLevel = row[LoyaltyCardTable.tierLevel],
            block = block,
            pause = pause,
            partnerName = "",
            cardColor = "#000000",
            logoUrl = null,
            visitsCount = row[LoyaltyCardTable.visitsCount]

        )
    }

    // 2. Получить список карт для Кошелька
    suspend fun getUserCards(userId: String): List<LoyaltyCardDto> = dbQuery {
        LoyaltyCardTable
            .join(
                otherTable = PartnersTable,
                joinType = JoinType.INNER,
                onColumn = LoyaltyCardTable.partnerId,
                otherColumn = PartnersTable.id
            )
            .selectAll().where { LoyaltyCardTable.userId eq userId }
            .map {
                rowToCardDto(it).copy(
                    partnerName = it[PartnersTable.businessName],
                    cardColor = it[PartnersTable.color].takeIf { it.isNotBlank() } ?: "#4F46E5",
                    logoUrl = it[PartnersTable.logoUrl],
                    visitsTarget = it[PartnersTable.defaultVisitsTarget]
                )
            }
    }

    // 2. Найти точки, где я Кассир
    suspend fun getCashierJobs(userId: String): List<CashierJobEntity> = dbQuery {
        // 1. Соединяем Кассиров и Точки (тут связь одна, innerJoin сработает сам)
        PartnerCashiersTable.innerJoin(TradingPointsTable)
            // 2. А вот Партнеров присоединяем ЯВНО, указывая колонки
            .join(
                otherTable = PartnersTable,
                joinType = JoinType.INNER,
                onColumn = TradingPointsTable.partnerId, // <-- Ключ в таблице Точек
                otherColumn = PartnersTable.id           // <-- Ключ в таблице Партнеров
            )
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

        // 1. ПРОВЕРКА: Является ли он сотрудником платформы?
        // Вот эта строка падала, потому что была без транзакции:
        val systemStaff =
            SystemStaffTable.selectAll().where { SystemStaffTable.userId eq userId }
                .singleOrNull()

        if (systemStaff != null) {
            val role = systemStaff[SystemStaffTable.role]

            workspaces.add(
                UserWorkspace(
                    id = "platform_admin_panel",
                    title = "LoyaltyLoop (Platform)",
                    role = role,
                    requirePin = false
                )
            )
        }

        // 2. Владения (Вызываем другие методы, это ок, транзакция поддержит вложенность)
        // Но лучше, если getPartnersByOwner тоже внутри вызывает dbQuery,
        // Exposed обработает вложенную транзакцию нормально.
        val ownedBusinesses =
            PartnersTable.selectAll().where { PartnersTable.ownerId eq userId }
                .map {
                    PartnerEntity(
                        id = it[PartnersTable.id],
                        businessName = it[PartnersTable.businessName],
                        hasPin = !it[PartnersTable.adminPinHash].isNullOrBlank(),
                        status = it[PartnersTable.status],
                        logoUrl = it[PartnersTable.logoUrl],
                        color = it[PartnersTable.color],
                        ownerId = it[PartnersTable.ownerId],
                        burnBonusesDays = it[PartnersTable.burnBonusesDays],
                        downgradeTierDays = it[PartnersTable.downgradeTierDays],
                        countryCode = it[PartnersTable.countryCode]
                    )
                }

        ownedBusinesses.forEach { p ->
            workspaces.add(
                UserWorkspace(
                    id = p.id,
                    title = p.businessName,
                    role = UserRole.PARTNER_ADMIN,
                    requirePin = true
                )
            )
        }

        // 3. Работа кассиром (Тут тоже перепишем на прямой запрос для чистоты транзакции)
        val cashierJobs = PartnerCashiersTable.innerJoin(TradingPointsTable)
            .join(
                otherTable = PartnersTable,
                joinType = JoinType.INNER,
                onColumn = TradingPointsTable.partnerId, // Явно говорим: бери партнера из Точки
                otherColumn = PartnersTable.id
            )
            .selectAll().where { PartnerCashiersTable.userId eq userId }
            .map {
                CashierJobEntity(
                    tradingPointId = it[TradingPointsTable.id],
                    pointName = it[TradingPointsTable.name],
                    businessName = it[PartnersTable.businessName]
                )
            }

        cashierJobs.forEach { job ->
            workspaces.add(
                UserWorkspace(
                    id = job.tradingPointId,
                    title = "${job.businessName} (${job.pointName})",
                    role = UserRole.CASHIER,
                    requirePin = false
                )
            )
        }

        // 4. Работа Менеджером Партнера
        val managerJobs = PartnerManagersTable
            .join(PartnersTable, JoinType.INNER, PartnerManagersTable.partnerId, PartnersTable.id)
            .selectAll().where { PartnerManagersTable.userId eq userId }
            .map {
                 val businessName = it[PartnersTable.businessName]
                 val partnerId = it[PartnersTable.id]
                 partnerId to businessName
            }
        
        managerJobs.forEach { (pId, pName) ->
            workspaces.add(
                UserWorkspace(
                    id = pId,
                    title = "$pName (Manager)", 
                    role = UserRole.PARTNER_MANAGER,
                    requirePin = true // Managers should use PIN if enabled on the partner account? Or their own?
                    // Currently workspaces.requirePin usually refers to the specific workspace auth.
                    // Let's set true.
                )
            )
        }

        workspaces
    }

    // Метод для сидинга (создания админа при старте)
    suspend fun createSystemStaff(userId: String, role: UserRole, defaultPinHash: String? = null) =
        dbQuery {
            if (!SystemStaffTable.selectAll().where { SystemStaffTable.userId eq userId }.empty()) {
                throw LoyaltyException(AppErrorCode.ALREADY_JOINED, "User already has a platform role")
            }

            SystemStaffTable.insert {
                it[id] = UUID.randomUUID().toString()
                it[this.userId] = userId
                it[this.role] = role
                it[pinHash] = defaultPinHash
            }
            true
        }

    // Сохранить новый рефреш токен
    suspend fun saveRefreshToken(token: String, userId: String, expiresAt: Long) = dbQuery {
        RefreshTokensTable.insert {
            it[RefreshTokensTable.token] = token
            it[RefreshTokensTable.userId] = userId
            it[RefreshTokensTable.expiresAt] = expiresAt
        }
    }

    // Проверить наличие токена в базе (жив ли он?)
    suspend fun findRefreshToken(token: String): String? = dbQuery {
        RefreshTokensTable.selectAll().where { RefreshTokensTable.token eq token }
            .map { it[RefreshTokensTable.userId] }
            .singleOrNull()
    }

    // Удалить токен (при выходе или ротации)
    suspend fun deleteRefreshToken(token: String) = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.token eq token }
    }

    // Удалить все токены юзера (Logout all devices)
    suspend fun deleteAllTokensForUser(userId: String) = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
    }

    suspend fun deleteUser(userId: String): Boolean = dbQuery {
        RefreshTokensTable.deleteWhere { RefreshTokensTable.userId eq userId }
        PartnerCashiersTable.deleteWhere { PartnerCashiersTable.userId eq userId }
        PartnerManagersTable.deleteWhere { PartnerManagersTable.userId eq userId }
        SystemStaffTable.deleteWhere { SystemStaffTable.userId eq userId }
        LoyaltyCardTable.deleteWhere { LoyaltyCardTable.userId eq userId }
        UsersTable.deleteWhere { UsersTable.id eq userId } > 0
    }
}

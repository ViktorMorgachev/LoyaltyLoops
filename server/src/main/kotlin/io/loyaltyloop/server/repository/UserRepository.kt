package io.loyaltyloop.server.repository


import org.jetbrains.exposed.sql.selectAll
import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.CashiersTable
import io.loyaltyloop.server.database.tables.LoyaltyCardTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class UserRepository {

    // Добавить пользователя
    suspend fun createUser(dto: UserDto) = dbQuery {

        if (dto.qrSecret.isBlank()) {
            throw IllegalArgumentException("SECURITY ERROR: Cannot create user ${dto.phoneNumber}. QR Secret is missing or empty.")
        }
        UsersTable.insert {
            it[id] = dto.id.ifEmpty { UUID.randomUUID().toString() } // Генерируем ID если нет
            it[phoneNumber] = dto.phoneNumber
            it[countryCode] = dto.countryCode
            it[createdAt] = System.currentTimeMillis()
            it[language] = dto.language
            it[qrSecret] = dto.qrSecret

        }
    }

    // Обновление профиля
    suspend fun updateUserProfile(userDto: UserDto, lang: String?, request: UpdateProfileRequest) = dbQuery {
        UsersTable.update({ UsersTable.id eq userDto.id }) { dto->
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
        UsersTable.selectAll().map { rowToDto(it) }
    }

    suspend fun getUserByPhone(phone: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { UsersTable.phoneNumber eq phone }
            .map { rowToDto(it) }
            .singleOrNull()
    }

    suspend fun getUserById(userId: String): UserDto? = dbQuery {
        UsersTable.selectAll().where { UsersTable.id eq userId }
            .map { rowToDto(it) }
            .singleOrNull()
    }

    // Вспомогательная функция: превращает строку БД в класс Kotlin
    private fun rowToDto(row: ResultRow): UserDto {
        return UserDto(
            id = row[UsersTable.id],
            phoneNumber = row[UsersTable.phoneNumber],
            countryCode = row[UsersTable.countryCode],
            firstName = row[UsersTable.firstName],
            lastName = row[UsersTable.lastName],
            email = row[UsersTable.email],
            qrSecret = row[UsersTable.qrSecret],
            language = row[UsersTable.language]
        )
    }

    suspend fun findOrCreateCard(userId: String, partnerId: String): LoyaltyCardDto = dbQuery {
        // 1. Ищем существующую
        val existingRow = LoyaltyCardTable.select {
            (LoyaltyCardTable.userId eq userId) and (LoyaltyCardTable.partnerId eq partnerId)
        }.singleOrNull()

        if (existingRow != null) {
            return@dbQuery rowToCardDto(existingRow)
        }

        // 2. Создаем новую
        // TODO: Пока хардкод уровня = 1. В следующем шаге мы добавим чтение настроек.
        val newId = java.util.UUID.randomUUID().toString()

        LoyaltyCardTable.insert {
            it[id] = newId
            it[this.userId] = userId
            it[this.partnerId] = partnerId
            it[balance] = 0.0
            it[totalSpent] = 0.0
            it[tierLevel] = 1
            it[isBlocked] = false
        }

        // Возвращаем созданный объект
        // Мы можем собрать его вручную, чтобы не делать лишний select
        LoyaltyCardDto(
            id = newId,
            userId = userId,
            partnerId = partnerId,
            balance = 0.0,
            totalSpent = 0.0,
            tierLevel = 1,
            isBlocked = false
        )
    }

    // 1. Обновленный маппер (теперь учитывает данные партнера)
    private fun rowToCardDto(row: ResultRow): LoyaltyCardDto {
        // Используем getOrNull - это безопасно.
        // Если мы сделали JOIN с PartnersTable, данные вернутся.
        // Если нет - вернется null (или пустая строка через элвис-оператор).

        val partnerName = row.getOrNull(PartnersTable.businessName) ?: ""
        val partnerLogo = row.getOrNull(PartnersTable.logoUrl)

        // Цвет пока хардкодим (в будущем добавим поле color в PartnersTable или Settings)
        val cardColor = "#4F46E5"

        return LoyaltyCardDto(
            id = row[LoyaltyCardTable.id],
            userId = row[LoyaltyCardTable.userId],
            partnerId = row[LoyaltyCardTable.partnerId],
            balance = row[LoyaltyCardTable.balance],
            totalSpent = row[LoyaltyCardTable.totalSpent],
            tierLevel = row[LoyaltyCardTable.tierLevel],
            isBlocked = row[LoyaltyCardTable.isBlocked],
            isClosed = row[LoyaltyCardTable.isClosed],

            // UI данные
            partnerName = partnerName,
            cardColor = cardColor,
            logoUrl = partnerLogo
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
            .map { rowToCardDto(it) }
    }

    // 1. Найти бизнесы, где я Владелец
    suspend fun getPartnersByOwner(userId: String): List<PartnerEntity> = dbQuery {
        PartnersTable.selectAll().where { PartnersTable.ownerId eq userId }
            .map {
                PartnerEntity(
                    id = it[PartnersTable.id],
                    name = it[PartnersTable.businessName],
                    hasPin = !it[PartnersTable.adminPinHash].isNullOrBlank()
                )
            }
    }

    // 2. Найти точки, где я Кассир
    suspend fun getCashierJobs(userId: String): List<CashierJobEntity> = dbQuery {
        // 1. Соединяем Кассиров и Точки (тут связь одна, innerJoin сработает сам)
        CashiersTable.innerJoin(TradingPointsTable)
            // 2. А вот Партнеров присоединяем ЯВНО, указывая колонки
            .join(
                otherTable = PartnersTable,
                joinType = JoinType.INNER,
                onColumn = TradingPointsTable.partnerId, // <-- Ключ в таблице Точек
                otherColumn = PartnersTable.id           // <-- Ключ в таблице Партнеров
            )
            .selectAll().where { CashiersTable.userId eq userId }
            .map {
                CashierJobEntity(
                    tradingPointId = it[TradingPointsTable.id],
                    pointName = it[TradingPointsTable.name],
                    businessName = it[PartnersTable.businessName]
                )
            }
    }

    suspend fun getUserWorkspaces(userId: String): List<UserWorkspace> {
        val workspaces = mutableListOf<UserWorkspace>()

        // 1. Владения
        val ownedBusinesses = getPartnersByOwner(userId)
        ownedBusinesses.forEach { p ->
            workspaces.add(UserWorkspace(
                id = p.id,
                title = p.name,
                role = UserRole.PARTNER_ADMIN,
                requirePin = true
            ))
        }

        // 2. Работа кассиром
        val cashierJobs = getCashierJobs(userId)
        cashierJobs.forEach { job ->
            workspaces.add(UserWorkspace(
                id = job.tradingPointId,
                title = "${job.businessName} (${job.pointName})",
                role = UserRole.CASHIER,
                requirePin = false
            ))
        }

        return workspaces
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
}

data class PartnerEntity(val id: String, val name: String, val hasPin: Boolean)
data class CashierJobEntity(val tradingPointId: String, val pointName: String, val businessName: String)
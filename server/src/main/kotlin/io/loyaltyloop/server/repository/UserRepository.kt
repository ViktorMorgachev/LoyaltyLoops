package io.loyaltyloop.server.repository


import org.jetbrains.exposed.sql.selectAll
import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.CashiersTable
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.RefreshTokensTable
import io.loyaltyloop.server.database.tables.TradingPointsTable
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.UserWorkspace
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import java.util.UUID

class UserRepository {

    // Добавить пользователя
    suspend fun createUser(dto: UserDto) = dbQuery {
        UsersTable.insert {
            it[id] = dto.id.ifEmpty { UUID.randomUUID().toString() } // Генерируем ID если нет
            it[phoneNumber] = dto.phoneNumber
            it[countryCode] = dto.countryCode
            it[createdAt] = System.currentTimeMillis()
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
            countryCode = row[UsersTable.countryCode]
        )
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
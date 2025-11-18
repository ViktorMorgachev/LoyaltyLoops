package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.UsersTable
import io.loyaltyloop.shared.models.UserDto
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

class UserRepository {

    // Добавить пользователя
    suspend fun createUser(dto: UserDto) = dbQuery {
        UsersTable.insert {
            it[id] = dto.id.ifEmpty { UUID.randomUUID().toString() } // Генерируем ID если нет
            it[phoneNumber] = dto.phoneNumber
            it[role] = dto.role
            it[countryCode] = dto.countryCode
            it[createdAt] = System.currentTimeMillis()
        }
    }

    // Получить всех (для теста)
    suspend fun getAllUsers(): List<UserDto> = dbQuery {
        UsersTable.selectAll().map { rowToDto(it) }
    }

    // Вспомогательная функция: превращает строку БД в класс Kotlin
    private fun rowToDto(row: ResultRow): UserDto {
        return UserDto(
            id = row[UsersTable.id],
            phoneNumber = row[UsersTable.phoneNumber],
            role = row[UsersTable.role],
            countryCode = row[UsersTable.countryCode]
        )
    }
}
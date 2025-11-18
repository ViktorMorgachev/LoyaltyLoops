package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    // id будет строкой (UUID), так как мы планируем много данных
    val id = varchar("id", 50)

    val phoneNumber = varchar("phone_number", 20).uniqueIndex() // Уникальный номер
    val role = enumerationByName("role", 20, UserRole::class) // Enum из shared модуля!
    val countryCode = varchar("country_code", 4) // KG, KZ

    // Техническое поле
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
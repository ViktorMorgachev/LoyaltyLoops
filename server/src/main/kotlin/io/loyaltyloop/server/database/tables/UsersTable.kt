package io.loyaltyloop.server.database.tables


import org.jetbrains.exposed.sql.Table


object UsersTable : Table("users") {
    val id = varchar("id", 50)
    val phoneNumber = varchar("phone_number", 20).uniqueIndex()
    val countryCode = varchar("country_code", 4)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
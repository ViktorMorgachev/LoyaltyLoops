package io.loyaltyloop.server.database.tables


import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = varchar("id", 50)
    val phoneNumber = varchar("phone_number", 20).uniqueIndex()
    val countryCode = varchar("country_code", 4)
    val createdAt = long("created_at")

    // --- НОВЫЕ ПОЛЯ ---
    val firstName = varchar("first_name", 50).nullable()
    val lastName = varchar("last_name", 50).nullable()
    val email = varchar("email", 100).nullable()

    val qrSecret = varchar("qr_secret", 64)
    val language = varchar("language", 5).default("ru") // "ru", "en", "ky"

    val isSuperAdmin = bool("is_super_admin").default(false)
    val isManager = bool("is_manager").default(false)
    val telegramId = long("telegram_id").nullable().uniqueIndex()
    val frozenUntil = long("frozen_until").nullable()
    val isDeleted = bool("is_deleted").default(false)
    val deletionReason = text("deletion_reason").nullable()
    // ------------------

    override val primaryKey = PrimaryKey(id)
}
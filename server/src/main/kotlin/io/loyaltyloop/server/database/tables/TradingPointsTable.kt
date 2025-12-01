package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.TradingPointType
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object TradingPointsTable : Table("trading_points") {
    val id = varchar("id", 50)
    val partnerId = varchar("partner_id", 50)
        .references(PartnersTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val address = varchar("address", 200).nullable()
    // ДЛЯ СОТРУДНИКОВ: Код для присоединения (например "JOIN-123")
    val inviteCode = varchar("invite_code", 20).nullable().uniqueIndex()

    val isActive = bool("is_active").default(false) // Оплачено?
    val isTemporarilyPaused = bool("is_temporarily_paused").default(false)


    // --- НОВЫЕ ПОЛЯ ---
    val type = enumerationByName("type", 20, TradingPointType::class).default(TradingPointType.OTHER)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val currency = varchar("currency", 10)
    val workingHoursJson = text("working_hours_json").nullable()
    val rating = double("rating").default(0.0)
    val ratingCount = integer("rating_count").default(0)

    val contactPhone = varchar("contact_phone", 30).nullable()
    val contactLink = varchar("contact_link", 50).nullable()
    val additionalInfo = varchar("additional_info", 20).nullable()

    override val primaryKey = PrimaryKey(id)
}
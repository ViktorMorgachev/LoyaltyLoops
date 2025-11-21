package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.PartnerStatus
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object PartnersTable : Table("partners") {
    val id = varchar("id", 50)
    val ownerId = varchar("owner_id", 50).references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val businessName = varchar("business_name", 100)
    val countryCode = varchar("country_code", 4)

    // ЗАЩИТА: Хэш пин-кода (4 цифры)
    val adminPinHash = varchar("admin_pin_hash", 128).nullable()

    val logoUrl = varchar("logo_url", 255).nullable()

    val status = enumerationByName("status", 20, PartnerStatus::class).default(PartnerStatus.PENDING)

    override val primaryKey = PrimaryKey(id)
}
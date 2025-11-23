package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object PartnerManagersTable : Table("partner_managers") {
    val id = varchar("id", 50)
    
    val userId = varchar("user_id", 50)
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    
    val partnerId = varchar("partner_id", 50)
        .references(PartnersTable.id, onDelete = ReferenceOption.CASCADE)
        
    val isActive = bool("is_active").default(true)
    
    override val primaryKey = PrimaryKey(id)
}


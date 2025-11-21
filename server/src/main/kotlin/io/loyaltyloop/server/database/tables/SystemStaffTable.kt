package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SystemStaffTable : Table("system_staff") {
    // ID записи сотрудника
    val id = varchar("id", 50)
    
    // Ссылка на глобального юзера
    val userId = varchar("user_id", 50)
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
        .uniqueIndex() // Один юзер не может быть двумя сотрудниками сразу
    
    // Роль (SUPER_ADMIN или MANAGER)
    val role = enumerationByName("role", 50, UserRole::class)
    
    // ПИН-код для входа в админку (Хэш)
    val pinHash = varchar("pin_hash", 128).nullable()
    
    override val primaryKey = PrimaryKey(id)
}
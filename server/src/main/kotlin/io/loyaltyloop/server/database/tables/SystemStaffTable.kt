package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица Администраторов Системы (Super Users).
 * Сотрудники самой платформы LoyaltyLoop, а не партнеров.
 *
 * **Архитектура:**
 * 1. **Разделение властей:**
 *    Обычные юзеры находятся в `UsersTable`.
 *    Если юзер становится админом платформы, создается запись здесь.
 *    Это позволяет одному человеку быть и клиентом (покупать кофе), и админом (саппорт).
 *
 * 2. **Безопасность:**
 *    - `pinHash`: Дополнительный фактор защиты для критических операций (сброс паролей, рефанд).
 *    - `role`: Определяет уровень доступа (Support, Admin, SuperAdmin).
 *
 * 3. **Единственность:**
 *    Индекс `uniqueIndex(user)` гарантирует, что у одного пользователя
 *    только одна системная роль.
 */
// TODO checked
object SystemStaffTable : UUIDTable("system_staff") {
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
        .uniqueIndex()
    val role = enumerationByName("role", 50, UserRole::class)
    val pinHash = varchar("pin_hash", 128).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}
package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Единая таблица персонала (Штат сотрудников).
 *
 * **Ролевая модель:**
 * 1. **PARTNER_MANAGER:**
 *    - Область видимости: Весь бизнес (все точки).
 *    - Поле `tradingPoint`: NULL.
 *    - Права: Управление кассирами, просмотр статистики, редактирование точек.
 *    - Ограничения: Не может уволить других менеджеров, не может удалить бизнес,
 *      не может менять критические настройки (если canEditSettings = false).
 *
 * 2. **Совместительство (Multi-role):**
 *    Благодаря составному индексу `(user, partner, role, tradingPoint)`,
 *    один User может быть одновременно:
 *    - Менеджером (запись с point=NULL)
 *    - Кассиром на Точке А (запись с point=A)
 *    - Кассиром на Точке Б (запись с point=B)
 *
 * 3. **Soft Delete (Увольнение):**
 *    При увольнении мы не удаляем строку (чтобы не ломать `TransactionsHistoryTable`),
 *    а выставляем `isActive = false`. Это сохраняет историю: "Кто продал этот товар? - Азамат (уволен)".
 */
// TODO checked
object PartnerStaffTable : UUIDTable("partner_staff") {
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val tradingPoint = reference("trading_point_id", TradingPointsTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val role = enumerationByName("role", 30, UserRole::class)
    val isActive = bool("is_active").default(true)
    val canRefund = bool("can_refund").default(false)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)

    init {
        uniqueIndex(user, partner, role, tradingPoint)
    }
}
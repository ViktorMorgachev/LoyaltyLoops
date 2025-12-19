package io.loyaltyloop.server.database.tables

import io.loyaltyloop.shared.models.DevicePlatform
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица токенов устройств (FCM) для Push-уведомлений.
 * Критически важна для маркетинга и операционных оповещений.
 *
 * **Важные архитектурные моменты:**
 * 1. **Контекст (Workspace Awareness):**
 *    Поля `partner` и `tradingPoint` определяют, в каком "режиме" сейчас находится устройство.
 *    - Если `activeRole = CASHIER` -> заполнено `tradingPoint`. Уведомления касаются работы кассы.
 *    - Если `activeRole = PARTNER_MANAGER` -> заполнено `partner`. Уведомления касаются бизнеса.
 *    - Если `activeRole = CUSTOMER` -> оба поля `NULL`. Уведомления касаются личных бонусов.
 *
 * 2. **Таргетинг:**
 *    Такая структура позволяет делать точечные рассылки:
 *    - *"Отправить всем кассирам точки X"* (`WHERE trading_point_id = X`).
 *    - *"Отправить всем менеджерам партнера Y"* (`WHERE partner_id = Y AND role = MANAGER`).
 *
 * 3. **Авто-очистка (CASCADE):**
 *    Если владелец удаляет Торговую Точку, токены всех кассиров, привязанных к ней,
 *    удаляются автоматически. "Мертвые" сессии не висят в базе.
 *
 * 4. **Уникальность:**
 *    Ключ уникальности — сам `token`. Это предотвращает дублирование, если пользователь
 *    переключается между ролями на одном устройстве (мы делаем Upsert).
 */
// TODO checked
object DeviceTokensTable : UUIDTable("device_tokens") {
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 500).uniqueIndex()
    val platform = enumerationByName("platform", 20, DevicePlatform::class) // ANDROID, IOS
    val activeRole = enumerationByName("active_role", 50, UserRole::class)
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE).nullable()
    val tradingPoint = reference("trading_point_id", TradingPointsTable, onDelete = ReferenceOption.CASCADE).nullable()
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}


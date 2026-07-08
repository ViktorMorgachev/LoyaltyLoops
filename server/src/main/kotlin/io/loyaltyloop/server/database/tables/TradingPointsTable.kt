package io.loyaltyloop.server.database.tables

import io.loyaltyloop.server.database.generateCode
import io.loyaltyloop.shared.models.TradingPointType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица Торговых Точек (Филиалов).
 * Физические или виртуальные места, где происходят транзакции.
 *
 * **Архитектурные моменты:**
 * 1. **Иерархия:**
 *    Точка всегда принадлежит Партнеру (`Partner`). Если Партнер удаляется (CASCADE),
 *    удаляются и все его точки. Бизнес не может существовать в вакууме.
 *
 * 2. **Invite Code (Onboarding):**
 *    Поле `inviteCode` позволяет кассирам самостоятельно привязываться к точке
 *    при регистрации, без ручного добавления админом.
 *    Это упрощает масштабирование сети (Self-service).
 *
 * 3. **Операционные статусы:**
 *    - `isActive`: Глобальный рубильник (например, за неуплату тарифа).
 *    - `isTemporarilyPaused`: Локальный статус (перерыв, ремонт), но подписка активна.
 *
 * 4. **Геоданные:**
 *    Храним координаты (`latitude`, `longitude`) для отображения на карте ("Рядом со мной").
 *    Используем `Double` (стандарт для GPS).
 */
// TODO checked
object TradingPointsTable : UUIDTable("trading_points") {
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val address = varchar("address", 200).nullable()
    val inviteCode = varchar("invite_code", 20)
        .uniqueIndex()
        .clientDefault {
            generateCode(suffix = "C-")
        }
    val isActive = bool("is_active").default(false)
    val isTemporarilyPaused = bool("is_temporarily_paused").default(false)
    val type = enumerationByName("type", 20, TradingPointType::class)
        .default(TradingPointType.OTHER)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val currency = varchar("currency", 3)
    val workingHoursJson = text("working_hours_json").nullable()
    val rating = double("rating").default(0.0)
    val ratingCount = integer("rating_count").default(0)
    val contactPhone = varchar("contact_phone", 30).nullable()
    val contactLink = varchar("contact_link", 50).nullable()
    val additionalInfo = varchar("additional_info", 50).nullable()
    val timezone = varchar("timezone", 30).default("UTC")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

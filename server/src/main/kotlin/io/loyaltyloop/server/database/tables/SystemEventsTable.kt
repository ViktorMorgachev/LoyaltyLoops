package io.loyaltyloop.server.database.tables

import io.loyaltyloop.server.models.SystemEventType
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица системных событий (Audit Log).
 * Хранит историю действий пользователей и системы для безопасности и отладки.
 *
 * **Архитектурные паттерны:**
 * 1. **Soft References (SET_NULL):**
 *    Логи — это исторический документ. Их нельзя удалять каскадно.
 *    Если `UsersTable` или `PartnersTable` удаляет запись, ссылка здесь становится NULL,
 *    но сама строка лога остается.
 *
 * 2. **Snapshotting:**
 *    Поле `userPhoneSnapshot` сохраняет номер телефона на момент события.
 *    Даже если юзер удалит свой аккаунт (ссылка `user` станет NULL), мы все равно
 *    будем знать, что действие совершил "+996555...", а не "Unknown".
 *
 * 3. **Payload:**
 *    Поле `payload` хранит детали в свободной форме (обычно JSON), например:
 *    `{"oldLevel": 1, "newLevel": 2}` при смене уровня.
 */
// TODO checked
object SystemEventsTable : UUIDTable("system_events") {
    val type = enumerationByName("type", 50, SystemEventType::class)
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val userPhoneSnapshot = varchar("user_phone_snapshot", 20).nullable()
    val payload = text("payload").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(isUnique = false, user, createdAt)
        index(isUnique = false, partner, createdAt)
        index(isUnique = false, type) // Для фильтра по типу события
    }
}


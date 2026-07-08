package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица Пользователей (Конечных клиентов).
 * Является центральной сущностью для авторизации и профиля.
 *
 * **Архитектурные паттерны:**
 * 1. **Универсальность (Identity):**
 *    Хранит как клиентов лояльности, так и владельцев бизнеса/кассиров.
 *    Роль определяется не здесь, а в таблицах связей (`PartnerStaffTable`, `SystemStaffTable`).
 *
 * 2. **Безопасность (PII):**
 *    - `phoneNumber`, `email`, `telegramId` имеют уникальные индексы для быстрого поиска и предотвращения дублей.
 *    - `qrSecret` — критичное поле для генерации динамических QR-кодов (TOTP-like),
 *      не должно "утекать" на клиент в открытом виде без необходимости.
 *
 * 3. **Soft Delete (Архивирование):**
 *    Вместо физического удаления (`DELETE FROM users`) используется флаг `isDeleted`.
 *    Это критично для финтеха: даже если пользователь ушел, его транзакции
 *    должны остаться валидными и ссылаться на (пусть и скрытого) владельца.
 *    Реальное удаление (GDPR) делается отдельным "скраббером", который затирает PII, но оставляет ID.
 */
// TODO checked
object UsersTable : UUIDTable("users") {
    val phoneNumber = varchar("phone_number", 20).uniqueIndex()
    val countryCode = varchar("country_code", 4).default("KG") // Можно дефолт
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val firstName = varchar("first_name", 50).nullable()
    val lastName = varchar("last_name", 50).nullable()
    val email = varchar("email", 100).nullable().uniqueIndex()
    val qrSecret = varchar("qr_secret", 64)
    val language = varchar("language", 5).default("ru")
    val telegramId = long("telegram_id").nullable().uniqueIndex()
    val frozenUntil = datetime("frozen_until").nullable()
    val isDeleted = bool("is_deleted").default(false)
    val deletionReason = text("deletion_reason").nullable()
}

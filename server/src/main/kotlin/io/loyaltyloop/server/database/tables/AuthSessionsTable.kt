package io.loyaltyloop.server.database.tables

import io.loyaltyloop.server.models.AuthSessionStatus
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица временных сессий авторизации.
 * Используется для сценариев:
 * 1. Вход через Telegram (Deep Link)
 * 2. Вход через Сканирование QR-кода (на кассе или веб-сайте)
 *
 * **Ключевые особенности:**
 * 1. **ID как Токен:** Поле `id` (UUID) является публичным секретом.
 *    Оно передается в Telegram Link (`start=login_{uuid}`) или зашивается в QR.
 *    Бэкенд ожидает подтверждения сессии именно с этим ID.
 *
 * 2. **Жизненный цикл:**
 *    - `PENDING`: Сессия создана, ждем действий пользователя (нажатие в ТГ / скан QR).
 *    - `CONFIRMED`: Пользователь подтвердил вход. Поля `telegramId/phone` и `user` заполнены.
 *      Клиент, опрашивающий статус сессии (polling), получает токены доступа.
 *    - `EXPIRED`: Время истекло, сессия невалидна.
 *
 * 3. **Очистка:** Поле `expiresAt` индексировано для быстрого удаления
 *    старых записей фоновым процессом (`CleanupJob`).
 */
// TODO checked
object AuthSessionsTable : UUIDTable("auth_sessions") {
    val status = enumerationByName("status", 20, AuthSessionStatus::class)
        .default(AuthSessionStatus.PENDING)
    val telegramId = long("telegram_id").nullable().index() // Индекс для быстрого поиска
    val phone = varchar("phone", 20).nullable()
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val expiresAt = datetime("expires_at").index()
}


package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица Refresh-токенов (Долгоживущих сессий).
 *
 * **Безопасность:**
 * 1. **Rotation:** Токены должны быть одноразовыми (Rotated).
 *    При каждом обновлении access-токена старый refresh-токен удаляется, создается новый.
 *    Это защищает от кражи (если украденный токен используют, валидный юзер вылетит, и мы узнаем об атаке).
 *
 * 2. **Auto-Cleanup:**
 *    Поле `expiresAt` индексировано. Фоновый процесс раз в сутки делает:
 *    `DELETE FROM refresh_tokens WHERE expires_at < NOW()`.
 *
 * 3. **Audit:**
 *    Поля `userAgent` и `ipAddress` нужны для отображения списка сессий пользователю
 *    и для анализа подозрительной активности.
 */
// TODO checked
object RefreshTokensTable : UUIDTable("refresh_tokens") {
    val token = varchar("token", 2048).uniqueIndex()
    val user = reference("user_id", UsersTable, onDelete = ReferenceOption.CASCADE)
    val expiresAt = datetime("expires_at").index()
    val userAgent = varchar("user_agent", 255).nullable()
    val ipAddress = varchar("ip_address", 45).nullable() // IPv6 fit
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

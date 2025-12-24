package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица Листа Ожидания (Pre-launch).
 * Собирает контакты заинтересованных пользователей до официального запуска или открытия регистрации.
 *
 * **Архитектурные моменты:**
 * 1. **Минимализм:**
 *    Хранит только критически важные данные для контакта (Email).
 *    Не требует создания полноценного аккаунта в `UsersTable`.
 *
 * 2. **Воронка (Funnel):**
 *    Поля `isInvited` и `invitedAt` превращают таблицу в простую CRM.
 *    Мы знаем, кому уже отправили инвайт, а кто еще ждет очереди.
 *
 * 3. **Идемпотентность:**
 *    Уникальный индекс на `email` предотвращает дублирование заявок от одного человека.
 */
// TODO checked
object WaitlistTable : UUIDTable("waitlist") {
    val email = varchar("email", 255).uniqueIndex()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val isInvited = bool("is_invited").default(false)
    val invitedAt = datetime("invited_at").nullable()
}


package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Таблица Чатов Поддержки (Threads).
 * Один Партнер = Один Тред (обычно).
 *
 * **Оптимизация (Denormalization):**
 * Чтобы не делать тяжелые JOIN-ы при отрисовке списка чатов в админке,
 * мы храним краткую информацию о последнем сообщении прямо здесь:
 * - `lastMessageSnippet`: Текст для превью ("Здравствуйте, у меня пробл...").
 * - `lastMessageAt`: Время для сортировки списка.
 *
 * **Счетчики:**
 * Поля `unreadForPartner` и `unreadForAdmin` позволяют мгновенно показывать
 * бейджики (red dots) без пересчета непрочитанных сообщений в таблице history.
 */
// TODO checked
object SupportThreadsTable : UUIDTable("support_threads") {
    val partner = reference("partner_id", PartnersTable, onDelete = ReferenceOption.CASCADE)
    val topic = varchar("topic", 200).nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime) // Для сортировки списка чатов
    val lastMessageSnippet = varchar("last_message_snippet", 255).nullable()
    val lastMessageAt = datetime("last_message_at").nullable()
    val unreadForPartner = integer("unread_for_partner").default(0)
    val unreadForAdmin = integer("unread_for_admin").default(0)
    val isClosed = bool("is_closed").default(false)
}

/**
 * Таблица Сообщений чата.
 *
 * **Логика работы:**
 * 1. **Направление (`isFromPartner`):**
 *    Вместо сложных ролей мы используем бинарный флаг:
 *    - `true`: Сообщение от бизнеса к платформе.
 *    - `false`: Ответ техподдержки бизнесу.
 *
 * 2. **Статус (`isRead`):**
 *    Флаг прочтения интерпретируется в зависимости от направления.
 *    Если сообщение от Партнера и `isRead = false`, значит Админ его еще не видел.
 *
 * 3. **Сохранность (`SET_NULL`):**
 *    `senderUserId` может стать NULL, если пользователь удален.
 *    Текст сообщения (`content`) при этом сохраняется.
 */
// TODO checked
object SupportMessagesTable : UUIDTable("support_messages") {
    val thread = reference("thread_id", SupportThreadsTable, onDelete = ReferenceOption.CASCADE)
    val senderUserId = reference("sender_user_id", UsersTable, onDelete = ReferenceOption.SET_NULL).nullable()
    val isFromPartner = bool("is_from_partner")
    val content = text("content")
    val attachments = text("attachments").nullable()
    val isRead = bool("is_read").default(false)
    val readAt = datetime("read_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

    init {
        index(isUnique = false, thread, createdAt)
    }
}


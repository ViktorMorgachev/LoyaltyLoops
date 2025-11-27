package io.loyaltyloop.server.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SupportThreadsTable : Table("support_threads") {
    val id = varchar("id", 50)
    val partnerId = varchar("partner_id", 50)
        .references(PartnersTable.id, onDelete = ReferenceOption.CASCADE)
        .uniqueIndex()
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val lastMessage = text("last_message").nullable()
    val lastMessageAt = long("last_message_at").nullable()
    val unreadForPartner = integer("unread_for_partner").default(0)
    val unreadForAdmin = integer("unread_for_admin").default(0)
    val isClosed = bool("is_closed").default(false)

    override val primaryKey = PrimaryKey(id)
}

object SupportMessagesTable : Table("support_messages") {
    val id = varchar("id", 50)
    val threadId = varchar("thread_id", 50)
        .references(SupportThreadsTable.id, onDelete = ReferenceOption.CASCADE)
    val senderUserId = varchar("sender_user_id", 50)
        .references(UsersTable.id, onDelete = ReferenceOption.CASCADE)
    val senderRole = varchar("sender_role", 50)
    val content = text("content")
    val createdAt = long("created_at")
    val isFromPartner = bool("is_from_partner")
    val readByPartner = bool("read_by_partner").default(false)
    val readByAdmin = bool("read_by_admin").default(false)

    override val primaryKey = PrimaryKey(id)
}


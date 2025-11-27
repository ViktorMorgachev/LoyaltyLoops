package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.SupportMessagesTable
import io.loyaltyloop.server.database.tables.SupportThreadsTable
import io.loyaltyloop.shared.models.SupportMessageDto
import io.loyaltyloop.shared.models.SupportThreadDto
import io.loyaltyloop.shared.models.SupportThreadResponse
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.util.UUID

class SupportChatRepository {

    data class MessageInsertResult(
        val thread: SupportThreadDto,
        val message: SupportMessageDto
    )

    private val threadQuery = SupportThreadsTable
        .join(PartnersTable, JoinType.INNER, onColumn = SupportThreadsTable.partnerId, otherColumn = PartnersTable.id)

    suspend fun getOrCreateThread(partnerId: String): SupportThreadDto = dbQuery {
        val existing = threadQuery
            .select { SupportThreadsTable.partnerId eq partnerId }
            .limit(1)
            .singleOrNull()

        if (existing != null) {
            return@dbQuery existing.toThreadDto()
        }

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        SupportThreadsTable.insert {
            it[this.id] = id
            it[this.partnerId] = partnerId
            it[createdAt] = now
            it[updatedAt] = now
            it[lastMessage] = null
            it[lastMessageAt] = null
            it[unreadForAdmin] = 0
            it[unreadForPartner] = 0
            it[isClosed] = false
        }

        threadQuery
            .select { SupportThreadsTable.id eq id }
            .single()
            .toThreadDto()
    }

    suspend fun getThreadById(threadId: String): SupportThreadDto? = dbQuery {
        threadQuery
            .select { SupportThreadsTable.id eq threadId }
            .limit(1)
            .singleOrNull()
            ?.toThreadDto()
    }

    suspend fun listThreads(): List<SupportThreadDto> = dbQuery {
        threadQuery
            .selectAll()
            .orderBy(SupportThreadsTable.lastMessageAt to SortOrder.DESC)
            .map { it.toThreadDto() }
    }

    suspend fun listMessages(threadId: String, limit: Int = 100): List<SupportMessageDto> = dbQuery {
        SupportMessagesTable
            .select { SupportMessagesTable.threadId eq threadId }
            .orderBy(SupportMessagesTable.createdAt to SortOrder.ASC)
            .limit(limit)
            .map { it.toMessageDto() }
    }

    suspend fun appendMessage(
        threadId: String,
        senderId: String,
        senderRole: UserRole,
        content: String,
        isFromPartner: Boolean
    ): MessageInsertResult = dbQuery {
        val now = System.currentTimeMillis()
        val messageId = UUID.randomUUID().toString()

        SupportMessagesTable.insert {
            it[id] = messageId
            it[this.threadId] = threadId
            it[this.senderUserId] = senderId
            it[this.senderRole] = senderRole.name
            it[this.content] = content
            it[this.createdAt] = now
            it[this.isFromPartner] = isFromPartner
            it[this.readByPartner] = !isFromPartner
            it[this.readByAdmin] = isFromPartner
        }

        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId }) {
            it[lastMessage] = content.take(500)
            it[lastMessageAt] = now
            it[updatedAt] = now
            if (isFromPartner) {
                it[SupportThreadsTable.unreadForAdmin] = SupportThreadsTable.unreadForAdmin + 1
                it[SupportThreadsTable.unreadForPartner] = 0
            } else {
                it[SupportThreadsTable.unreadForPartner] = SupportThreadsTable.unreadForPartner + 1
                it[SupportThreadsTable.unreadForAdmin] = 0
            }
        }

        val thread = threadQuery.select { SupportThreadsTable.id eq threadId }.single().toThreadDto()

        MessageInsertResult(
            thread = thread,
            message = SupportMessageDto(
                id = messageId,
                threadId = threadId,
                senderId = senderId,
                senderRole = senderRole,
                content = content,
                createdAt = now,
                isFromPartner = isFromPartner
            )
        )
    }

    suspend fun markReadByPartner(threadId: String) = dbQuery {
        SupportMessagesTable.update({ (SupportMessagesTable.threadId eq threadId) and (SupportMessagesTable.readByPartner eq false) }) {
            it[readByPartner] = true
        }
        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId }) {
            it[unreadForPartner] = 0
        }
    }

    suspend fun markReadByAdmin(threadId: String) = dbQuery {
        SupportMessagesTable.update({ (SupportMessagesTable.threadId eq threadId) and (SupportMessagesTable.readByAdmin eq false) }) {
            it[readByAdmin] = true
        }
        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId }) {
            it[unreadForAdmin] = 0
        }
    }

    suspend fun getThreadWithMessages(threadId: String, limit: Int = 100): SupportThreadResponse? {
        val thread = getThreadById(threadId) ?: return null
        val messages = listMessages(threadId, limit)
        return SupportThreadResponse(thread, messages)
    }

    suspend fun getThreadForPartner(partnerId: String, limit: Int = 100): SupportThreadResponse {
        val thread = getOrCreateThread(partnerId)
        val messages = listMessages(thread.id, limit)
        return SupportThreadResponse(thread, messages)
    }

    private fun ResultRow.toThreadDto(): SupportThreadDto = SupportThreadDto(
        id = this[SupportThreadsTable.id],
        partnerId = this[SupportThreadsTable.partnerId],
        partnerName = this[PartnersTable.businessName],
        lastMessageSnippet = this[SupportThreadsTable.lastMessage],
        lastMessageAt = this[SupportThreadsTable.lastMessageAt],
        unreadForPartner = this[SupportThreadsTable.unreadForPartner],
        unreadForAdmin = this[SupportThreadsTable.unreadForAdmin],
        isClosed = this[SupportThreadsTable.isClosed]
    )

    private fun ResultRow.toMessageDto(): SupportMessageDto = SupportMessageDto(
        id = this[SupportMessagesTable.id],
        threadId = this[SupportMessagesTable.threadId],
        senderId = this[SupportMessagesTable.senderUserId],
        senderRole = UserRole.valueOf(this[SupportMessagesTable.senderRole]),
        content = this[SupportMessagesTable.content],
        createdAt = this[SupportMessagesTable.createdAt],
        isFromPartner = this[SupportMessagesTable.isFromPartner]
    )
}


package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.PartnersTable
import io.loyaltyloop.server.database.tables.SupportMessagesTable
import io.loyaltyloop.server.database.tables.SupportThreadsTable
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toThreadDto
import io.loyaltyloop.server.utils.toUUID
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.SupportMessageDto
import io.loyaltyloop.shared.models.SupportThreadDto
import io.loyaltyloop.shared.models.UserRole
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

// TODO checked
class SupportChatRepository {

    data class MessageInsertResult(
        val thread: SupportThreadDto,
        val message: SupportMessageDto
    )
    private val threadQuery = SupportThreadsTable.innerJoin(PartnersTable)

    suspend fun sendMessage(
        partnerId: String,
        senderUserId: String,
        content: String,
        isFromPartner: Boolean
    ): MessageInsertResult = dbQuery {
        val partnerUuid = partnerId.toUUID()
        val userUuid = senderUserId.toUUID()
        val now = nowUtc()

        var threadId = SupportThreadsTable
            .slice(SupportThreadsTable.id)
            .select { SupportThreadsTable.partner eq partnerUuid }
            .singleOrNull()
            ?.get(SupportThreadsTable.id)

        if (threadId == null) {
            threadId = SupportThreadsTable.insertAndGetId {
                it[partner] = partnerUuid
                it[createdAt] = now
                it[updatedAt] = now
                it[topic] = "Support Chat"
                it[isClosed] = false
                it[unreadForAdmin] = 0
                it[unreadForPartner] = 0
            }
        }

        val messageId = SupportMessagesTable.insertAndGetId {
            it[thread] = threadId
            it[this.senderUserId] = userUuid
            it[this.content] = content
            it[this.isFromPartner] = isFromPartner
            it[isRead] = false
            it[createdAt] = now
        }

        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId }) {
            it[lastMessageSnippet] = content.take(100)
            it[lastMessageAt] = now
            it[updatedAt] = now
            it[isClosed] = false

            // Инкремент счетчиков
            with(SqlExpressionBuilder) {
                if (isFromPartner) {
                    // Пишет партнер -> +1 непрочитанное у Админа
                    it.update(unreadForAdmin, unreadForAdmin + 1)
                } else {
                    // Пишет админ -> +1 непрочитанное у Партнера
                    it.update(unreadForPartner, unreadForPartner + 1)
                }
            }
        }

        val threadRow = threadQuery.select { SupportThreadsTable.id eq threadId }.single()
        val threadDto = threadRow.toThreadDto()


        val messageDto = SupportMessageDto(
            id = messageId.value.toString(),
            threadId = threadId.value.toString(),
            senderId = senderUserId,
            senderRole = if (isFromPartner) UserRole.PARTNER_ADMIN else UserRole.PLATFORM_MANAGER,
            content = content,
            createdAt = now.toUtcMillis(),
            isFromPartner = isFromPartner
        )

        MessageInsertResult(threadDto, messageDto)
    }

    /**
     * Пометить чат как прочитанный.
     * Сбрасывает счетчики и обновляет флаги сообщений.
     */
    suspend fun markAsRead(threadId: String, isReaderPartner: Boolean) = dbQuery {
        val threadUuid = threadId.toUUID()
        val now = nowUtc()

        // 1. Сбрасываем счетчик в Треде
        SupportThreadsTable.update({ SupportThreadsTable.id eq threadUuid }) {
            if (isReaderPartner) {
                it[unreadForPartner] = 0
            } else {
                it[unreadForAdmin] = 0
            }
        }
        val targetMessagesFromPartner = !isReaderPartner

        SupportMessagesTable.update({
            (SupportMessagesTable.thread eq threadUuid) and
                    (SupportMessagesTable.isFromPartner eq targetMessagesFromPartner) and
                    (SupportMessagesTable.isRead eq false)
        }) {
            it[isRead] = true
            it[readAt] = now
        }
    }

    suspend fun closeThread(threadId: String) = dbQuery {
        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId.toUUID() }) {
            it[isClosed] = true
        }
    }

    suspend fun getThreads(
        partnerId: String? = null,
        limit: Int = 20,
        offset: Long = 0
    ): List<SupportThreadDto> = dbQuery {
        val query = threadQuery.selectAll()

        if (partnerId != null) {
            query.andWhere { SupportThreadsTable.partner eq partnerId.toUUID() }
        }

        query.orderBy(SupportThreadsTable.updatedAt to SortOrder.DESC)
            .limit(limit, offset)
            .map { row -> row.toThreadDto() }
    }

    suspend fun getMessages(threadId: String, limit: Int = 50, offset: Long = 0): List<SupportMessageDto> = dbQuery {
        val threadUuid = threadId.toUUID()

        SupportMessagesTable
            .select { SupportMessagesTable.thread eq threadUuid }
            .orderBy(SupportMessagesTable.createdAt, SortOrder.DESC)
            .limit(limit, offset)
            .map { row ->
                val isFromPartner = row[SupportMessagesTable.isFromPartner]

                SupportMessageDto(
                    id = row[SupportMessagesTable.id].value.toString(),
                    threadId = row[SupportMessagesTable.thread].value.toString(),
                    senderId = row[SupportMessagesTable.senderUserId]?.value?.toString() ?: "",
                    senderRole = if (isFromPartner) UserRole.PARTNER_ADMIN else UserRole.PLATFORM_MANAGER,
                    content = row[SupportMessagesTable.content],
                    createdAt = row[SupportMessagesTable.createdAt].toUtcMillis(),
                    isFromPartner = row[SupportMessagesTable.isFromPartner]
                )
            }.reversed()
    }

    // --- HELPERS ---
    suspend fun getThreadById(threadId: String): SupportThreadDto? = dbQuery {
        threadQuery
            .select { SupportThreadsTable.id eq threadId.toUUID() }
            .singleOrNull()
            ?.toThreadDto()
    }

}

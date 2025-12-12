package io.loyaltyloop.server.repository

import io.loyaltyloop.server.database.DatabaseFactory.dbQuery
import io.loyaltyloop.server.database.tables.*
import io.loyaltyloop.shared.models.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.UUID

class SupportChatRepository {

    data class MessageInsertResult(
        val thread: SupportThreadDto,
        val message: SupportMessageDto
    )

    // Приватный Query для переиспользования (DRY)
    // Джойним PartnersTable, чтобы получить имя бизнеса для заголовка чата
    private val threadQuery = SupportThreadsTable
        .join(PartnersTable, JoinType.INNER, onColumn = SupportThreadsTable.partnerId, otherColumn = PartnersTable.id)

    suspend fun getOrCreateThread(partnerId: String): SupportThreadDto = dbQuery {
        // 1. Пытаемся найти существующий тред
        val existing = threadQuery
            .selectAll()
            .where { SupportThreadsTable.partnerId eq partnerId }
            .singleOrNull()

        if (existing != null) {
            return@dbQuery existing.toThreadDto()
        }

        // 2. Если не нашли — создаем новый
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Используем try-catch на случай "гонки", если два запроса пришли одновременно
        // и пытаются создать тред для одного партнера (uniqueIndex защитит)
        try {
            SupportThreadsTable.insert {
                it[id] = newId
                it[this.partnerId] = partnerId
                it[createdAt] = now
                it[updatedAt] = now
                it[lastMessage] = null
                it[lastMessageAt] = null
                it[unreadForAdmin] = 0
                it[unreadForPartner] = 0
                it[isClosed] = false
            }
        } catch (e: Exception) {
            // Если упали (Unique Constraint), значит тред уже создан в параллельном потоке.
            // Просто возвращаем его.
            return@dbQuery threadQuery
                .selectAll()
                .where { SupportThreadsTable.partnerId eq partnerId }
                .single()
                .toThreadDto()
        }

        // Возвращаем только что созданный (делаем select, чтобы подтянуть данные из join)
        threadQuery
            .selectAll()
            .where { SupportThreadsTable.id eq newId }
            .single()
            .toThreadDto()
    }

    suspend fun getThreadById(threadId: String): SupportThreadDto? = dbQuery {
        threadQuery
            .selectAll()
            .where { SupportThreadsTable.id eq threadId }
            .singleOrNull()
            ?.toThreadDto()
    }

    suspend fun listThreads(): List<SupportThreadDto> = dbQuery {
        threadQuery
            .selectAll()
            // Сортируем: сначала те, где были недавние сообщения (nulls last)
            .orderBy(SupportThreadsTable.lastMessageAt to SortOrder.DESC_NULLS_LAST)
            .limit(200) // Разумное ограничение для админки
            .map { it.toThreadDto() }
    }

    suspend fun listMessages(threadId: String, limit: Int = 100): List<SupportMessageDto> = dbQuery {
        SupportMessagesTable
            .selectAll()
            .where { SupportMessagesTable.threadId eq threadId }
            .orderBy(SupportMessagesTable.createdAt to SortOrder.ASC) // Хронологический порядок
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

        // 1. Вставляем сообщение
        SupportMessagesTable.insert {
            it[id] = messageId
            it[this.threadId] = threadId
            it[this.senderUserId] = senderId
            it[this.senderRole] = senderRole.name
            it[this.content] = content
            it[createdAt] = now
            it[this.isFromPartner] = isFromPartner
            it[readByPartner] = !isFromPartner // Если отправил партнер, то он уже "прочитал"
            it[readByAdmin] = isFromPartner    // Если отправил админ, то он уже "прочитал"
        }

        // 2. Обновляем тред (атомарно инкрементируем счетчики)
        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId }) {
            it[lastMessage] = content.take(500) // Сохраняем превью (обрезаем если слишком длинное)
            it[lastMessageAt] = now
            it[updatedAt] = now

            with(SqlExpressionBuilder) {
                if (isFromPartner) {
                    // Пишет Партнер -> Увеличиваем счетчик Админа, сбрасываем счетчик Партнера
                    it.update(unreadForAdmin, unreadForAdmin + 1)
                    it[unreadForPartner] = 0
                } else {
                    // Пишет Админ -> Увеличиваем счетчик Партнера, сбрасываем счетчик Админа
                    it.update(unreadForPartner, unreadForPartner + 1)
                    it[unreadForAdmin] = 0
                }
            }
        }

        // 3. Возвращаем обновленные данные
        val thread = threadQuery.selectAll().where { SupportThreadsTable.id eq threadId }.single().toThreadDto()

        val messageDto = SupportMessageDto(
            id = messageId,
            threadId = threadId,
            senderId = senderId,
            senderRole = senderRole,
            content = content,
            createdAt = now,
            isFromPartner = isFromPartner
        )

        MessageInsertResult(thread, messageDto)
    }

    // Помечаем, что Партнер прочитал сообщения
    suspend fun markReadByPartner(threadId: String) = dbQuery {
        // 1. Обновляем статус сообщений
        SupportMessagesTable.update({
            (SupportMessagesTable.threadId eq threadId) and (SupportMessagesTable.readByPartner eq false)
        }) {
            it[readByPartner] = true
        }
        // 2. Сбрасываем счетчик в треде
        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId }) {
            it[unreadForPartner] = 0
        }
    }

    // Помечаем, что Админ прочитал сообщения
    suspend fun markReadByAdmin(threadId: String) = dbQuery {
        SupportMessagesTable.update({
            (SupportMessagesTable.threadId eq threadId) and (SupportMessagesTable.readByAdmin eq false)
        }) {
            it[readByAdmin] = true
        }
        SupportThreadsTable.update({ SupportThreadsTable.id eq threadId }) {
            it[unreadForAdmin] = 0
        }
    }

    // --- Helpers / Facades ---

    suspend fun getThreadWithMessages(threadId: String, limit: Int = 100): SupportThreadResponse? {
        val thread = getThreadById(threadId) ?: return null
        val messages = listMessages(threadId, limit)
        return SupportThreadResponse(thread, messages)
    }

    // Удобный метод для API партнера
    suspend fun getThreadForPartner(partnerId: String, limit: Int = 100): SupportThreadResponse {
        val thread = getOrCreateThread(partnerId)
        val messages = listMessages(thread.id, limit)
        return SupportThreadResponse(thread, messages)
    }

    // --- MAPPERS ---

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
        senderRole = try {
            UserRole.valueOf(this[SupportMessagesTable.senderRole])
        } catch (e: Exception) {
            UserRole.PARTNER_ADMIN // Fallback, если роль была удалена из enum
        },
        content = this[SupportMessagesTable.content],
        createdAt = this[SupportMessagesTable.createdAt],
        isFromPartner = this[SupportMessagesTable.isFromPartner]
    )
}
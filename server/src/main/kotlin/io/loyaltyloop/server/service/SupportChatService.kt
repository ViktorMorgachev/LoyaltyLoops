package io.loyaltyloop.server.service

import io.loyaltyloop.server.repository.SupportChatRepository
import io.loyaltyloop.shared.models.SupportChatEventDto
import io.loyaltyloop.shared.models.SupportChatEventType
import io.loyaltyloop.shared.models.SupportMessageDto
import io.loyaltyloop.shared.models.SupportThreadDto
import io.loyaltyloop.shared.models.SupportThreadResponse
import io.loyaltyloop.shared.models.UserRole
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.server.utils.json
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.shared.models.AppErrorCode
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap

// TODO checked
class SupportChatService(
    private val repository: SupportChatRepository,
    private val partnerRepository: PartnerRepository
) {
    private val adminSessions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()
    private val partnerSessions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()


    suspend fun getPartnerThread(partnerId: String): SupportThreadResponse {

        val threads = repository.getThreads(partnerId, limit = 1)

        val thread = threads.firstOrNull()

        if (thread == null) {
            val partner = partnerRepository.getPartnerByIdOrThrow(partnerId)

            // Mock thread for UI (пока сообщения нет, тред не создан в БД)
            return SupportThreadResponse(
                thread = SupportThreadDto(
                    id = "",
                    partnerId = partnerId,
                    partnerName = partner.businessName,
                    lastMessageSnippet = null,
                    lastMessageAt = null,
                    unreadForPartner = 0,
                    unreadForAdmin = 0,
                    isClosed = false,
                    createdAt = nowUtc().toUtcMillis()
                ),
                messages = emptyList()
            )
        }

        val messages = repository.getMessages(thread.id, limit = 50)

        // Сразу помечаем прочитанным, так как партнер открыл чат
        repository.markAsRead(thread.id, isReaderPartner = true)

        return SupportThreadResponse(thread, messages)
    }

    suspend fun sendPartnerMessage(partnerId: String, senderId: String, content: String): SupportMessageDto {
        val result = repository.sendMessage(partnerId, senderId, content, isFromPartner = true)
        broadcastEvent(result.thread, result.message)
        return result.message
    }

    // --- ADMIN SIDE ---

    suspend fun listThreads(): List<SupportThreadDto> =
        repository.getThreads(limit = 50)

    suspend fun getAdminThread(threadId: String): SupportThreadResponse {
        // [FIX] Передаем false, так как смотрит Админ
        val thread = repository.getThreadById(threadId)
            ?: throw LoyaltyException(AppErrorCode.NOT_FOUND, "Thread not found")

        val messages = repository.getMessages(threadId, limit = 50)

        repository.markAsRead(threadId, isReaderPartner = false)

        return SupportThreadResponse(thread, messages)
    }

    suspend fun sendAdminMessage(threadId: String, senderId: String, content: String): SupportMessageDto {
        val thread = repository.getThreadById(threadId)
            ?: throw IllegalArgumentException("Thread not found")

        val result = repository.sendMessage(thread.partnerId, senderId, content, isFromPartner = false)
        broadcastEvent(result.thread, result.message)
        return result.message
    }

    // --- REALTIME ---

    suspend fun registerPartnerSession(partnerId: String, session: DefaultWebSocketServerSession) {
        partnerSessions.computeIfAbsent(partnerId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    suspend fun unregisterPartnerSession(partnerId: String, session: DefaultWebSocketServerSession) {
        partnerSessions[partnerId]?.remove(session)
        if (partnerSessions[partnerId]?.isEmpty() == true) {
            partnerSessions.remove(partnerId)
        }
    }

    suspend fun registerAdminSession(userId: String, session: DefaultWebSocketServerSession) {
        adminSessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    suspend fun unregisterAdminSession(userId: String, session: DefaultWebSocketServerSession) {
        adminSessions[userId]?.remove(session)
        if (adminSessions[userId]?.isEmpty() == true) {
            adminSessions.remove(userId)
        }
    }

    private suspend fun broadcastEvent(thread: SupportThreadDto, message: SupportMessageDto) {
        val event = SupportChatEventDto(
            type = SupportChatEventType.MESSAGE_CREATED,
            thread = thread,
            message = message
        )
        val payload = json.encodeToString(event)

        // 1. Отправляем партнеру (всем подключенным менеджерам этого бизнеса)
        broadcastToPartners(thread.partnerId, payload)

        // 2. Отправляем ВСЕМ админам (так как у них общий инбокс)
        broadcastToAdmins(payload)
    }

    private suspend fun broadcastToPartners(partnerId: String, payload: String) {
        partnerSessions[partnerId]?.toList()?.forEach { session ->
            try {
                session.send(Frame.Text(payload))
            } catch (_: Exception) {
                // Игнорируем ошибки отправки, сессия закроется сама
            }
        }
    }

    private suspend fun broadcastToAdmins(payload: String) {
        // Рассылаем по всем активным админским сессиям
        adminSessions.values.flatten().forEach { session ->
            try {
                session.send(Frame.Text(payload))
            } catch (_: Exception) {
                // Игнорируем ошибки отправки, сессия закроется сама
            }
        }
    }
}


package io.loyaltyloop.server.service

import io.loyaltyloop.server.repository.SupportChatRepository
import io.loyaltyloop.shared.models.SupportChatEventDto
import io.loyaltyloop.shared.models.SupportChatEventType
import io.loyaltyloop.shared.models.SupportMessageDto
import io.loyaltyloop.shared.models.SupportThreadDto
import io.loyaltyloop.shared.models.SupportThreadResponse
import io.loyaltyloop.shared.models.UserRole
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class SupportChatService(
    private val repository: SupportChatRepository
) {

    private val json = Json { encodeDefaults = true }
    private val adminSessions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()
    private val partnerSessions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun getPartnerThread(partnerId: String): SupportThreadResponse {
        val response = repository.getThreadForPartner(partnerId)
        repository.markReadByPartner(response.thread.id)
        return response
    }

    suspend fun getAdminThread(threadId: String): SupportThreadResponse? {
        val response = repository.getThreadWithMessages(threadId)
        if (response != null) {
            repository.markReadByAdmin(threadId)
        }
        return response
    }

    suspend fun listThreads(): List<SupportThreadDto> = repository.listThreads()

    suspend fun sendPartnerMessage(partnerId: String, senderId: String, senderRole: UserRole, content: String): SupportMessageDto {
        val thread = repository.getOrCreateThread(partnerId)
        val result = repository.appendMessage(thread.id, senderId, senderRole, content, isFromPartner = true)
        broadcastEvent(result.thread, result.message)
        return result.message
    }

    suspend fun sendAdminMessage(threadId: String, senderId: String, senderRole: UserRole, content: String): SupportMessageDto {
        val result = repository.appendMessage(threadId, senderId, senderRole, content, isFromPartner = false)
        broadcastEvent(result.thread, result.message)
        return result.message
    }

    suspend fun markPartnerRead(partnerId: String) {
        val thread = repository.getOrCreateThread(partnerId)
        repository.markReadByPartner(thread.id)
    }

    suspend fun markAdminRead(threadId: String) {
        repository.markReadByAdmin(threadId)
    }

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
        broadcastToPartners(thread.partnerId, payload)
        broadcastToAdmins(payload)
    }

    private suspend fun broadcastToPartners(partnerId: String, payload: String) {
        partnerSessions[partnerId]?.toList()?.forEach { session ->
            runCatching {
                session.send(payload)
            }.onFailure {
                removeSession(partnerSessions, session)
            }
        }
    }

    private suspend fun broadcastToAdmins(payload: String) {
        val targets = adminSessions.values.flatMap { it.toList() }
        targets.forEach { session ->
            runCatching {
                session.send(payload)
            }.onFailure {
                removeSession(adminSessions, session)
            }
        }
    }

    private fun removeSession(
        storage: ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>,
        session: DefaultWebSocketServerSession
    ) {
        storage.entries.toList().forEach { (key, set) ->
            if (set.remove(session) && set.isEmpty()) {
                storage.remove(key, set)
            }
        }
    }
}


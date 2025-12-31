package io.loyaltyloop.server.service

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.loyaltyloop.shared.models.CardRealtimePayload
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import io.ktor.websocket.send

// TODO checked
class CardRealtimeService {

    private val sessions = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()
    private val mutex = Mutex()
    private val json = Json { encodeDefaults = true }
    private val logger = org.slf4j.LoggerFactory.getLogger(CardRealtimeService::class.java)

    suspend fun register(userId: String, session: DefaultWebSocketServerSession) {
        mutex.withLock {
            val set = sessions.getOrPut(userId) { mutableSetOf() }
            set.add(session)
        }
    }

    suspend fun unregister(userId: String, session: DefaultWebSocketServerSession) {
        mutex.withLock {
            val set = sessions[userId]
            set?.remove(session)
            if (set != null && set.isEmpty()) {
                sessions.remove(userId)
            }
        }
    }

    suspend fun notifyUser(userId: String, payload: CardRealtimePayload) {
        val serialized = json.encodeToString(payload)
        val targetSessions = sessions[userId]?.toList().orEmpty()
        if (targetSessions.isEmpty()) {
            logger.info("Realtime notify skipped: no sessions for user=$userId event=${payload.eventType} cardId=${payload.cardId}")
            return
        }

        logger.info("Realtime notify: user=$userId sessions=${targetSessions.size} event=${payload.eventType} cardId=${payload.cardId}")
        targetSessions.forEach { session ->
            runCatching { session.send(serialized) }
        }
    }
}


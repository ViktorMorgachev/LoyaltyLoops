package io.loyaltyloop.server.service

import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.SystemEventRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class EventLogger(
    private val repository: SystemEventRepository
) {
    private val logger = LoggerFactory.getLogger(EventLogger::class.java)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun log(
        type: SystemEventType,
        userId: String? = null,
        userPhone: String? = null,
        partnerId: String? = null,
        payload: String? = null
    ) {
        // Fire and forget in coroutine to not block main flow
        scope.launch {
            try {
                repository.logEvent(type, userId, userPhone, partnerId, payload)
                // Optionally log to stdout/file as well
                logger.info("EVENT [${type.name}] User: $userId/$userPhone Partner: $partnerId Payload: $payload")
            } catch (e: Exception) {
                logger.error("Failed to log system event", e)
            }
        }
    }
}


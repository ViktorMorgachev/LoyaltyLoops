package io.loyaltyloop.server.service

import org.slf4j.LoggerFactory

interface EmailService {
    suspend fun sendPinResetEmail(email: String, resetLink: String)
}

class ConsoleEmailService : EmailService {
    private val logger = LoggerFactory.getLogger(ConsoleEmailService::class.java)

    override suspend fun sendPinResetEmail(email: String, resetLink: String) {
        logger.info("PIN reset link for $email: $resetLink")
    }
}


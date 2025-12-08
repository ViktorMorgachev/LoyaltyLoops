package io.loyaltyloop.server.service.email

import io.loyaltyloop.server.service.EmailDebugStore
import org.slf4j.LoggerFactory

interface EmailService {
    suspend fun sendEmail(to: String, subject: String, body: String)
    suspend fun sendPinResetEmail(email: String, resetLink: String)
}

class ConsoleEmailService : EmailService {
    private val logger = LoggerFactory.getLogger(ConsoleEmailService::class.java)

    override suspend fun sendEmail(to: String, subject: String, body: String) {
        // In production, integrate with SendGrid, AWS SES, Mailgun, etc.
        logger.info(
            """=== EMAIL SENT to $to ===
            To: $to
            Subject: $subject
            Body: $body
            ==================
        """.trimIndent()
        )
    }

    override suspend fun sendPinResetEmail(email: String, resetLink: String) {
        EmailDebugStore.capturePinReset(resetLink)
        sendEmail(email, "PIN Reset Request", "Click here to reset your PIN: $resetLink")
    }
}


package io.loyaltyloop.server.service.sms

import org.slf4j.LoggerFactory

class ConsoleSmsService : SmsService {
    private val logger = LoggerFactory.getLogger("SMS_CONSOLE")

    override suspend fun sendSms(phone: String, text: String): Boolean {
        logger.info("📨 [SMS] To: $phone | Body: $text")
        return true
    }
}
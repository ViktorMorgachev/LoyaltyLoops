package io.loyaltyloop.server.service.sms

import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.sms.verification.VerificationSignals
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import org.slf4j.LoggerFactory

class ConsoleSmsService(private val otpService: OtpService,
                         private val eventLogger: EventLogger,
                         private val systemEventRepository: SystemEventRepository) : SmsService {

    private val MAX_OTP_ATTEMPTS = 3
    private val OTP_BLOCK_DURATION_MS = 3600000L // 1 hour
    private val logger = LoggerFactory.getLogger("SMS_CONSOLE")

    override suspend fun sendSms(phone: String, text: String): Boolean {
        logger.info("📨 [SMS] To: $phone | Body: $text")
        return true
    }

    override suspend fun startVerification(
        phone: String,
        userId: String?,
        signals: VerificationSignals?
    ): String {
        checkOtpBlock(phone)

        // Anti-fraud checks
        val now = System.currentTimeMillis()

        // 1. Limit: Max 1 request per 60 seconds
        val recentCount = systemEventRepository.countEvents(
            type = SystemEventType.SMS_REQUEST,
            userPhone = phone,
            since = now - 60_000 // last minute
        )
        if (recentCount >= 1) {
            throw LoyaltyException(AppErrorCode.TOO_MANY_REQUESTS, "Wait before resending SMS")
        }

        // 2. Limit: Max 5 requests per hour
        val hourlyCount = systemEventRepository.countEvents(
            type = SystemEventType.SMS_REQUEST,
            userPhone = phone,
            since = now - 3_600_000 // last hour
        )
        if (hourlyCount >= 5) {
            throw LoyaltyException(AppErrorCode.TOO_MANY_REQUESTS, "Too many SMS attempts. Try again later.")
        }

        val code = otpService.generateCode(phone)
        // 3. Шлем в консоль
        sendSms(phone, "Your code: $code")

        val ipInfo = signals?.ip?.let { " [IP: $it]" } ?: ""
        eventLogger.log(
            type = SystemEventType.SMS_REQUEST,
            userId = userId,
            userPhone = phone,
            payload = "SMS sent with code: $code$ipInfo"
        )

        // 4. Возвращаем номер телефона как ID проверки
        return phone
    }

    override suspend fun checkCode(
        verificationId: String?,
        phone: String,
        code: String
    ): Boolean {
        checkOtpBlock(phone)

        val isValid = otpService.validateCode(phone, code)
        if (!isValid) {
            eventLogger.log(
                type = SystemEventType.OTP_VERIFICATION_FAILED,
                userPhone = verificationId,
                payload = "Invalid code entered"
            )
        }
        return isValid
    }

    private suspend fun checkOtpBlock(phone: String) {
        val now = System.currentTimeMillis()
        val failures = systemEventRepository.countEvents(
            type = SystemEventType.OTP_VERIFICATION_FAILED,
            userPhone = phone,
            since = now - OTP_BLOCK_DURATION_MS
        )
        if (failures >= MAX_OTP_ATTEMPTS) {
            throw LoyaltyException(AppErrorCode.OTP_ATTEMPTS_EXCEEDED, "Too many failed attempts. Blocked for 1 hour.")
        }
    }
}
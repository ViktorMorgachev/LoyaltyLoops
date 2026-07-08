package io.loyaltyloop.server.service.sms

import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.models.VerificationSignals
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import org.slf4j.LoggerFactory

// TODO checked
data class SmsRateLimits(
    val maxPerMinute: Int,
    val maxPerHour: Int,
    val maxFailedAttempts: Int,
    val blockDurationMs: Long
)

class ConsoleSmsService(
    private val otpService: OtpService,
    private val eventLogger: EventLogger,
    private val systemEventRepository: SystemEventRepository,
    private val limits: SmsRateLimits
) : SmsService {

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

        val now = System.currentTimeMillis()

        val recentCount = systemEventRepository.countEvents(
            type = SystemEventType.SMS_REQUEST,
            userPhone = phone,
            since = now - 60_000 // last minute
        )
        if (recentCount >= limits.maxPerMinute) {
            throw LoyaltyException(AppErrorCode.TOO_MANY_REQUESTS, "Wait before resending SMS")
        }

        val hourlyCount = systemEventRepository.countEvents(
            type = SystemEventType.SMS_REQUEST,
            userPhone = phone,
            since = now - 3_600_000
        )
        if (hourlyCount >= limits.maxPerHour) {
            throw LoyaltyException(AppErrorCode.TOO_MANY_REQUESTS, "Too many SMS attempts. Try again later.")
        }

        val code = otpService.generateCode(phone)

        sendSms(phone, "Your code: $code")

        val ipInfo = signals?.ip?.let { " [IP: $it]" } ?: ""
        eventLogger.log(
            type = SystemEventType.SMS_REQUEST,
            userId = userId,
            userPhone = phone,
            payload = "SMS sent with code: $code$ipInfo"
        )

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
            since = now - limits.blockDurationMs
        )
        if (failures >= limits.maxFailedAttempts) {
            throw LoyaltyException(AppErrorCode.OTP_ATTEMPTS_EXCEEDED, "Too many failed attempts. Blocked for 1 hour.")
        }
    }
}

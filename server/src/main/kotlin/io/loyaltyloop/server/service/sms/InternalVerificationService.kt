package io.loyaltyloop.server.service.sms

import io.loyaltyloop.server.models.SystemEventType
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.sms.verification.VerificationService

import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode

class InternalVerificationService(
    private val smsService: SmsService,
    private val otpService: OtpService,
    private val eventLogger: EventLogger,
    private val systemEventRepository: SystemEventRepository
) : VerificationService {

    private val MAX_OTP_ATTEMPTS = 3
    private val OTP_BLOCK_DURATION_MS = 3600000L // 1 hour

    override suspend fun startVerification(phone: String): String {
        // 0. Check Block
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
        smsService.sendSms(phone, "Your code: $code")

        eventLogger.log(
            type = SystemEventType.SMS_REQUEST,
            userPhone = phone,
            payload = "SMS sent with code: $code"
        )

        // 4. Возвращаем номер телефона как ID проверки
        return phone
    }

    override suspend fun checkCode(verificationId: String, code: String): Boolean {
        // verificationId здесь равен phone
        checkOtpBlock(verificationId)

        val isValid = otpService.validateCode(verificationId, code)
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
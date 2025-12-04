package io.loyaltyloop.server.service.sms

import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.sms.verification.VerificationService

class InternalVerificationService(
    private val smsService: SmsService,
    private val otpService: OtpService
) : VerificationService {

    override suspend fun startVerification(phone: String): String {
        val code = otpService.generateCode(phone)
        // 3. Шлем в консоль
        smsService.sendSms(phone, "Your code: $code")

        // 4. Возвращаем номер телефона как ID проверки
        return phone
    }

    override suspend fun checkCode(verificationId: String, code: String): Boolean {
        // verificationId здесь равен phone
        return otpService.validateCode(verificationId, code)
    }
}
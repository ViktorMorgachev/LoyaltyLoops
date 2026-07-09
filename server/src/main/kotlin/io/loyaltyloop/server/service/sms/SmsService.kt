package io.loyaltyloop.server.service.sms

import io.loyaltyloop.server.models.VerificationSignals

// TODO checked
interface SmsService {
    suspend fun sendSms(phone: String, text: String): Boolean

    /**
     * Отправляет код.
     * @return Возвращает ID проверки (для Prelude это verification_id, для Internal - просто номер телефона)
     */
    suspend fun startVerification(
        phone: String,
        userId: String? = null,
        signals: VerificationSignals? = null
    ): String

    /**
     * Проверяет код.
     * @param verificationId Тот ID, который вернул startVerification
     */
    suspend fun checkCode(verificationId: String?, phone: String,  code: String): Boolean
}

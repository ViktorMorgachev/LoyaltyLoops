package io.loyaltyloop.server.service.sms.verification

interface VerificationService {
    /**
     * Отправляет код.
     * @return Возвращает ID проверки (для Prelude это verification_id, для Internal - просто номер телефона)
     */
    suspend fun startVerification(phone: String): String

    /**
     * Проверяет код.
     * @param verificationId Тот ID, который вернул startVerification
     */
    suspend fun checkCode(verificationId: String, code: String): Boolean
}
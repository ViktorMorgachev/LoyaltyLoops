package io.loyaltyloop.server.service

import io.ktor.server.config.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

// TODO checked
class OtpService(config: ApplicationConfig) {

    private val codeTtl = config.propertyOrNull("otp.ttl")?.getString()?.toLong() ?: 120_000L

    // Хранилище: Телефон -> Данные о коде
    private val otpStorage = ConcurrentHashMap<String, OtpEntry>()

    private data class OtpEntry(
        val code: String,
        val expiresAt: Long
    )

    /**
     * Генерирует случайный код, сохраняет его и возвращает.
     */
    fun generateCode(phone: String): String {
        // Генерируем 4 случайные цифры (1000..9999)
        val code = Random.nextInt(1000, 9999).toString()

        val expiresAt = System.currentTimeMillis() + codeTtl
        otpStorage[phone] = OtpEntry(code, expiresAt)

        return code
    }

    /**
     * Проверяет код. Если верен - удаляет его из памяти (одноразовость).
     */
    fun validateCode(phone: String, code: String): Boolean {
        val entry = otpStorage[phone] ?: return false

        if (System.currentTimeMillis() > entry.expiresAt) {
            otpStorage.remove(phone) // Удаляем протухший
            return false
        }

        if (entry.code == code) {
            otpStorage.remove(phone)
            return true
        }

        return false
    }
}

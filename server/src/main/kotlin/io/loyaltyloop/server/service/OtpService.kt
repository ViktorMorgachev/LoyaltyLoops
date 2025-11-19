package io.loyaltyloop.server.service

import io.ktor.server.config.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class OtpService(config: ApplicationConfig) {

    // Читаем TTL из конфига (по дефолту 2 минуты)
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

        // 1. Проверка времени
        if (System.currentTimeMillis() > entry.expiresAt) {
            otpStorage.remove(phone) // Удаляем протухший
            return false
        }

        // 2. Проверка совпадения
        if (entry.code == code) {
            otpStorage.remove(phone) // Код использован, удаляем
            return true
        }

        return false
    }
}
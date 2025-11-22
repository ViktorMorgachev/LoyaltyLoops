package io.loyaltyloop.shared.utils

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

actual object CryptoUtils {
    actual fun hmacSha256(key: String, data: String): String {
        try {
            val algorithm = "HmacSHA256"
            val secretKey = SecretKeySpec(key.toByteArray(), algorithm)
            val mac = Mac.getInstance(algorithm)
            mac.init(secretKey)
            val bytes = mac.doFinal(data.toByteArray())
            // Конвертируем байты в HEX строку
            return bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}
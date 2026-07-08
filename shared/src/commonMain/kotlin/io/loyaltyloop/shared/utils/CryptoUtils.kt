package io.loyaltyloop.shared.utils

expect object CryptoUtils {
    fun hmacSha256(key: String, data: String): String
}

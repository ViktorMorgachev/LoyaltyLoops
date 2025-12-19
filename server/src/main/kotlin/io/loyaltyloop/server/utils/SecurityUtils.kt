package io.loyaltyloop.server.utils

import java.security.MessageDigest
import java.util.UUID

// TODO checked
object SecurityUtils {

    fun hashPin(pin: String): String = hashValue(pin)

    fun hashValue(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isStrongPin(pin: String): Boolean {
        return pin.length in 4..12 && pin.all { it.isDigit() }
    }
    fun verifyPin(pinInput: String, storedHash: String): Boolean {
        val inputHash = hashPin(pinInput)
        return inputHash == storedHash
    }

    fun generateToken(): String = UUID.randomUUID().toString().replace("-", "")
}


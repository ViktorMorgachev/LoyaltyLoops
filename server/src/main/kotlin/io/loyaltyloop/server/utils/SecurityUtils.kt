package io.loyaltyloop.server.utils

import java.security.MessageDigest

object SecurityUtils {

    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isStrongPin(pin: String): Boolean {
        return pin.length in 4..12 && pin.all { it.isDigit() }
    }
}


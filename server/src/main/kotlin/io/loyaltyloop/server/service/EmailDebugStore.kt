package io.loyaltyloop.server.service

/**
 * Simple helper to keep the last PIN reset link for tests/debug.
 * Not exposed via API and only accessed from JVM tests.
 */
object EmailDebugStore {
    @Volatile
    var lastPinResetLink: String? = null

    fun capturePinReset(link: String) {
        lastPinResetLink = link
    }

    fun clear() {
        lastPinResetLink = null
    }
}


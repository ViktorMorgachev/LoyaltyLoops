package io.loyaltyloop.server.utils

import java.time.LocalDateTime
import java.time.ZoneOffset

// TODO checked
fun LocalDateTime.toUtcMillis(): Long {
    return this.toInstant(ZoneOffset.UTC).toEpochMilli()
}

fun Long.toUtcLocalDateTime(): LocalDateTime {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(this),
        ZoneOffset.UTC
    )
}

fun nowUtc(): LocalDateTime {
    return LocalDateTime.now(ZoneOffset.UTC)
}

package io.loyaltyloop.server.utils

import io.ktor.server.config.ApplicationConfig

// TODO checked
fun ApplicationConfig.string(path: String, default: String = ""): String =
    propertyOrNull(path)?.getString()?.trim()?.takeIf { it.isNotBlank() } ?: default

fun ApplicationConfig.bool(path: String, default: Boolean = false): Boolean =
    propertyOrNull(path)?.getString()?.trim()?.toBooleanStrictOrNull() ?: default

fun ApplicationConfig.int(path: String, default: Int): Int =
    propertyOrNull(path)?.getString()?.trim()?.toIntOrNull() ?: default

fun ApplicationConfig.long(path: String, default: Long): Long =
    propertyOrNull(path)?.getString()?.trim()?.toLongOrNull() ?: default

fun ApplicationConfig.double(path: String, default: Double): Double =
    propertyOrNull(path)?.getString()?.trim()?.toDoubleOrNull() ?: default


package io.loyaltyloop.server.database

fun generateCode(suffix: String = "M-"): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return suffix + (1..6).map { chars.random() }.joinToString("")
}

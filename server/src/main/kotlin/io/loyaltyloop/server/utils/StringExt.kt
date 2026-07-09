package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.ClientRatingTag
import java.util.UUID

// TODO checked
fun String?.toClientRatingTags(): List<ClientRatingTag> = this.splitCsv().mapNotNull { tagStr ->
    try {
        ClientRatingTag.valueOf(tagStr)
    } catch (_: IllegalArgumentException) {
        null
    }
}

// TODO checked
fun String?.splitCsv(): List<String> =
    this
        ?.split(",")
        ?.mapNotNull { it.trim().takeIf { tag -> tag.isNotEmpty() } }
        ?: emptyList()


@Throws(LoyaltyException::class)
fun String.toUUID(): UUID {
    return try {
        UUID.fromString(this)
    } catch (e: IllegalArgumentException) {
        throw LoyaltyException(AppErrorCode.INVALID_REQUEST, "Invalid UUID format: $this", e)
    }
}

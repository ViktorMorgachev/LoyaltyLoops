package io.loyaltyloop.server.utils

import io.loyaltyloop.shared.models.AppErrorCode

// TODO checked
class LoyaltyException(
    val code: AppErrorCode,
    message: String? = null
) : RuntimeException(message ?: code.name)


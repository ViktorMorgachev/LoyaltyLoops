package io.loyaltyloop.server.service.sms.verification


data class VerificationSignals(
    val ip: String? = null,
    val deviceId: String? = null,
    val platform: String? = null,
    val deviceModel: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null
)

package io.loyaltyloop.server.models

import kotlinx.serialization.Serializable

enum class SystemEventType {
    LOGIN,
    REGISTER,
    SMS_REQUEST,
    ACCRUAL,
    REDEMPTION,
    TIER_CHANGE,
    VISIT,
    ERROR,
    INFO,
    OTP_VERIFICATION_FAILED,
    PIN_CHANGE_SUCCESS,
    PIN_RESET_REQUEST,
    PIN_RESET_SUCCESS,
    PIN_VERIFICATION_FAILED
}

@Serializable
data class SystemEvent(
    val id: String,
    val type: SystemEventType,
    val userId: String?,
    val userPhone: String?,
    val partnerId: String?,
    val payload: String?, // JSON or text
    val timestamp: Long
)

@Serializable
data class SystemEventFilter(
    val type: SystemEventType? = null,
    val userId: String? = null,
    val userPhone: String? = null,
    val partnerId: String? = null,
    val from: Long? = null,
    val to: Long? = null,
    val limit: Int = 100,
    val offset: Long = 0
)


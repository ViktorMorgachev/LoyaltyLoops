package io.loyaltyloop.server.models

import kotlinx.serialization.Serializable

enum class SystemEventType {
    LOGIN,
    REGISTER,
    DELETING,
    SMS_REQUEST, // Added
    OTP_VERIFICATION_FAILED,

    // Transactions
    ACCRUAL,
    REDEMPTION,

    EMAIL_SEND_SUCCESS,
    EMAIL_SEND_ERROR,

    SMS_SEND_SUCCESS,
    SMS_SEND_ERROR,
    TIER_CHANGE,
    VISIT,

    // Security
    PIN_CHANGE_SUCCESS,
    TIER_UPGRADED,
    PIN_RESET_REQUEST,
    PIN_RESET_SUCCESS,
    PIN_VERIFICATION_FAILED,


    // System
    ERROR,
    INFO,
    WARNING,
    NOTIFICATION_SENT,

    // [NEW] B2B / Platform Events (из нашей новой логики)
    PARTNER_BLOCKED,
    PARTNER_UNBLOCKED,

    POINT_ACTIVATED,
    POINT_DEACTIVATED,
    POINT_ACTIVATION_REJECTED,
    POINT_ACTIVATION_CONFIRMED,

    SUBSCRIPTION_EXPIRED,
    SUBSCRIPTION_ACTIVATED,
    SUBSCRIPTION_CREATED,
    SUBSCRIPTION_REJECTED,
    SUBSCRIPTION_WARNING_SENT, // Письмо за 3 дня

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

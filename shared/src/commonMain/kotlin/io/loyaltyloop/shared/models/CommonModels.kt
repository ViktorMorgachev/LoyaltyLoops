package io.loyaltyloop.shared.models

import kotlinx.serialization.Serializable

enum class AppErrorCode {
    // Common
    SUCCESS,
    UNKNOWN_ERROR,
    INTERNAL_ERROR,
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    ACCOUNT_FROZEN,
    EMAIL_NOT_SET,
    INVALID_RESET_TOKEN,

    // Auth
    INVALID_PHONE,
    INVALID_CODE,
    CODE_EXPIRED,
    TOKEN_EXPIRED,
    USER_NOT_FOUND,
    USER_CREATION_FAILED,
    INVALID_PIN,

    // Business
    BUSINESS_ALREADY_EXISTS,
    BUSINESS_NOT_FOUND,
    POINT_NOT_FOUND,
    POINT_INACTIVE,
    ALREADY_JOINED,
    INVALID_INVITE_CODE,
    
    // Terminal
    QR_EXPIRED,
    INVALID_QR_SIGNATURE,
    SECURITY_QR_SECRET_MISSING,
    INVALID_AMOUNT,
    INVALID_TIER_VALUE,
    LOYALTY_SETTING_NOT_FOUND,
    CARD_NOT_FOUND
}


@Serializable
data class ApiMessage(
    val code: AppErrorCode,
    val message: String? = null
)

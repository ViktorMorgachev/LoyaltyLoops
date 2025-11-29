package io.loyaltyloop.app.utils

import io.loyaltyloop.shared.models.AppErrorCode
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

fun AppErrorCode.toResource(): StringResource {
    return when (this) {
        AppErrorCode.SUCCESS -> Res.string.error_success
        AppErrorCode.UNKNOWN_ERROR -> Res.string.error_unknown_error
        AppErrorCode.INTERNAL_ERROR -> Res.string.error_internal_error
        AppErrorCode.INVALID_REQUEST -> Res.string.error_invalid_request
        AppErrorCode.UNAUTHORIZED -> Res.string.error_unauthorized
        AppErrorCode.FORBIDDEN -> Res.string.error_forbidden
        AppErrorCode.NOT_FOUND -> Res.string.error_not_found
        AppErrorCode.ACCOUNT_FROZEN -> Res.string.error_account_frozen
        AppErrorCode.EMAIL_NOT_SET -> Res.string.error_email_required
        AppErrorCode.INVALID_RESET_TOKEN -> Res.string.error_invalid_reset_token
        AppErrorCode.INVALID_PHONE -> Res.string.error_invalid_phone
        AppErrorCode.INVALID_CODE -> Res.string.error_invalid_code
        AppErrorCode.CODE_EXPIRED -> Res.string.error_code_expired
        AppErrorCode.USER_NOT_FOUND -> Res.string.error_user_not_found
        AppErrorCode.INVALID_PIN -> Res.string.error_invalid_pin
        AppErrorCode.USER_CREATION_FAILED -> Res.string.error_user_creation_failed
        AppErrorCode.BUSINESS_ALREADY_EXISTS -> Res.string.error_business_already_exists
        AppErrorCode.BUSINESS_NOT_FOUND -> Res.string.error_business_not_found
        AppErrorCode.POINT_NOT_FOUND -> Res.string.error_point_not_found
        AppErrorCode.POINT_INACTIVE -> Res.string.error_point_inactive
        AppErrorCode.ALREADY_JOINED -> Res.string.error_already_joined
        AppErrorCode.INVALID_INVITE_CODE -> Res.string.error_invalid_invite_code
        AppErrorCode.QR_EXPIRED -> Res.string.error_qr_expired
        AppErrorCode.INVALID_QR_SIGNATURE -> Res.string.error_invalid_qr_signature
        AppErrorCode.SECURITY_QR_SECRET_MISSING -> Res.string.error_security_qr_secret_missing
        AppErrorCode.INVALID_AMOUNT -> Res.string.error_invalid_amount
        AppErrorCode.INVALID_TIER_VALUE -> Res.string.error_invalid_tier_value
        AppErrorCode.LOYALTY_SETTING_NOT_FOUND -> Res.string.error_loyalty_setting_not_found
        AppErrorCode.CARD_NOT_FOUND -> Res.string.error_card_not_found
        AppErrorCode.CARD_BLOCKED -> Res.string.error_card_blocked
        AppErrorCode.CARD_PAUSED -> Res.string.error_card_paused
        AppErrorCode.TOKEN_EXPIRED -> Res.string.error_token_expired
        AppErrorCode.CARD_IS_BLOCKED -> Res.string.error_token_expired
    }
}


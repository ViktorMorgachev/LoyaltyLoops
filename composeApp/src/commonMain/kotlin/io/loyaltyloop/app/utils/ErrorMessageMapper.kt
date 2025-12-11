package io.loyaltyloop.app.utils

import io.loyaltyloop.shared.models.AppErrorCode
import loyaltyloop.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource

fun AppErrorCode.toResource(message: String? = null): StringResource {
    return when (this) {
        AppErrorCode.SMS_PROVIDER_ERROR -> {
            when (message) {
                "unsupported_country" -> Res.string.error_prelude_unsupported_country
                "channel_not_enabled_in_region" -> Res.string.error_prelude_channel_not_enabled_in_region
                "too_many_attempts" -> Res.string.error_prelude_too_many_attempts
                "too_many_checks" -> Res.string.error_prelude_too_many_checks
                "impossible_code" -> Res.string.error_prelude_impossible_code
                "insufficient_balance" -> Res.string.error_prelude_insufficient_balance
                "suspended_account" -> Res.string.error_prelude_suspended_account
                else -> Res.string.error_sms_provider_error
            }
        }
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
        AppErrorCode.INVALID_PHONE_NUMBER -> Res.string.error_invalid_phone_number
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
        AppErrorCode.POINT_PAUSED -> Res.string.error_point_paused
        AppErrorCode.TOO_MANY_REQUESTS -> Res.string.error_to_many_requests
        AppErrorCode.OTP_ATTEMPTS_EXCEEDED -> Res.string.error_otp_attempts_exceeded
        AppErrorCode.ACCOUNT_DELETED -> Res.string.error_account_deleted
        AppErrorCode.TRIAL_ALREADY_USED -> Res.string.ignore
        AppErrorCode.PARTNER_ON_REVIEW -> Res.string.ignore
        AppErrorCode.PARTNER_BLOCKED -> Res.string.error_partner_blocked
        // Moderation
        AppErrorCode.USER_BANNED -> Res.string.error_user_banned
        AppErrorCode.BLOCK_REQUEST_CREATED -> Res.string.term_report_success
        AppErrorCode.BLOCK_REQUEST_ALREADY_EXISTS -> Res.string.error_block_request_exists
    }
}


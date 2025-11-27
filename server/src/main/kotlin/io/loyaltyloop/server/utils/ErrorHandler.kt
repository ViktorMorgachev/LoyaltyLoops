package io.loyaltyloop.server.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandler")

suspend fun handleError(call: ApplicationCall, exception: Throwable) {
    // Don't log stacktrace for expected business exceptions unless debug
    if (exception !is LoyaltyException) {
        logger.error("Error handling request: ${call.request.uri}", exception)
    } else {
        logger.warn("Business Exception: ${exception.code} - ${exception.message} at ${call.request.uri}")
    }

    when (exception) {
        is LoyaltyException -> {
            val status = when (exception.code) {
                AppErrorCode.USER_NOT_FOUND -> HttpStatusCode.Unauthorized

                AppErrorCode.FORBIDDEN,
                AppErrorCode.ACCOUNT_FROZEN,
                AppErrorCode.CODE_EXPIRED,
                AppErrorCode.INVALID_PIN -> HttpStatusCode.Forbidden

                AppErrorCode.NOT_FOUND,
                AppErrorCode.BUSINESS_NOT_FOUND,
                AppErrorCode.POINT_NOT_FOUND,
                AppErrorCode.LOYALTY_SETTING_NOT_FOUND,
                AppErrorCode.CARD_NOT_FOUND -> HttpStatusCode.NotFound

                AppErrorCode.BUSINESS_ALREADY_EXISTS,
                AppErrorCode.ALREADY_JOINED,
                AppErrorCode.POINT_INACTIVE,
                AppErrorCode.INVALID_INVITE_CODE -> HttpStatusCode.Conflict

                AppErrorCode.INVALID_REQUEST,
                AppErrorCode.INVALID_PHONE,
                AppErrorCode.QR_EXPIRED,
                AppErrorCode.INVALID_QR_SIGNATURE,
                AppErrorCode.INVALID_AMOUNT,
                AppErrorCode.INVALID_TIER_VALUE,
                AppErrorCode.EMAIL_NOT_SET,
                AppErrorCode.INVALID_RESET_TOKEN,
                AppErrorCode.INVALID_CODE-> HttpStatusCode.BadRequest


                AppErrorCode.SECURITY_QR_SECRET_MISSING,
                AppErrorCode.USER_CREATION_FAILED,
                AppErrorCode.INTERNAL_ERROR,
                AppErrorCode.UNKNOWN_ERROR -> HttpStatusCode.InternalServerError

                AppErrorCode.SUCCESS -> HttpStatusCode.OK
                AppErrorCode.UNAUTHORIZED,
                AppErrorCode.TOKEN_EXPIRED -> {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiMessage(AppErrorCode.UNAUTHORIZED, exception.message)
                    )
                    return
                }

            }

            call.respond(
                status,
                ApiMessage(exception.code, exception.message)
            )


        }

        // Legacy mapping
        is IllegalArgumentException -> {
            call.respond(
                HttpStatusCode.BadRequest,
                ApiMessage(AppErrorCode.INVALID_REQUEST, exception.message)
            )
        }

        is IllegalStateException -> {
            call.respond(
                HttpStatusCode.Conflict,
                ApiMessage(AppErrorCode.INVALID_REQUEST, exception.message)
            )
        }

        is SecurityException -> {
            call.respond(
                HttpStatusCode.Forbidden,
                ApiMessage(AppErrorCode.FORBIDDEN, exception.message)
            )
        }

        is NoSuchElementException -> {
            call.respond(
                HttpStatusCode.NotFound,
                ApiMessage(AppErrorCode.NOT_FOUND, exception.message)
            )
        }

        else -> {
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiMessage(AppErrorCode.INTERNAL_ERROR, "Internal Server Error")
            )
        }
    }
}

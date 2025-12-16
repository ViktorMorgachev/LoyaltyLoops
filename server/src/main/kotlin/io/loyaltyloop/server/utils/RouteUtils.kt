package io.loyaltyloop.server.utils

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode

/**
 * Безопасно извлекает userId из JWT токена
 * @return userId или null, если токен невалиден
 */
fun ApplicationCall.getUserId(): String? {
    val principal = principal<JWTPrincipal>() ?: return null
    return principal.payload.getClaim("id")?.asString()
}

/**
 * Извлекает userId из JWT токена или отвечает 401
 */
suspend fun ApplicationCall.getUserIdOrRespond(
    userRepository: UserRepository? = null,
    allowFrozenActions: Boolean = false
): String? {
    val userId = getUserId()
    if (userId.isNullOrBlank()) {
        respond(
            HttpStatusCode.Unauthorized,
            ApiMessage(AppErrorCode.UNAUTHORIZED, "Invalid or missing token")
        )
        return null
    }
    if (userRepository != null) {
        val user = userRepository.getUserById(userId)
            ?: run {
                respond(
                    HttpStatusCode.Unauthorized,
                    ApiMessage(AppErrorCode.USER_NOT_FOUND, "User not found")
                )
                return null
            }

        user.isFrozenUntil?.also {
            if (!allowFrozenActions && isMutatingRequest() && it > System.currentTimeMillis()){
                respond(
                    HttpStatusCode.Forbidden,
                    ApiMessage(AppErrorCode.ACCOUNT_FROZEN, "Account is temporarily frozen")
                )
                return null
            }
        }

    }
    return userId
}

suspend fun ApplicationCall.getCurrencyForTimezone(): String {
    val timezoneId = request.header("X-Timezone-Id") ?: throw LoyaltyException(
        AppErrorCode.INVALID_REQUEST,
        "X-Timezone-Id header is required for currency conversion"
    )

    return TimezoneUtils.getCurrencyForTimezone(timezoneId)
}

private fun ApplicationCall.isMutatingRequest(): Boolean {
    return request.httpMethod in setOf(HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete, HttpMethod.Patch)
}

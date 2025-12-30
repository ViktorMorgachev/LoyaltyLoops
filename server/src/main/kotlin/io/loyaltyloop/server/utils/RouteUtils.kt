package io.loyaltyloop.server.utils

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.loyaltyloop.server.models.VerificationSignals
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.Country
import io.loyaltyloop.shared.models.CountryCode
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.util.UUID

// TODO checked
/**
 * Безопасно извлекает userId из JWT токена
 * @return userId или null, если токен невалиден
 */

fun ApplicationCall.getUserId(): String? {
    val principal = principal<JWTPrincipal>() ?: return null
    return principal.payload.getClaim("id")?.asString()
}

/**
* 1. Проверяет наличие токена.
* 2. Проверяет валидность UUID.
* 3. Проверяет существование юзера в БД.
* 4. Проверяет заморозку (Frozen).
*/

suspend fun ApplicationCall.getUserIdOrRespond(
    accessControlService: AccessControlService,
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

    val userUuid = try {
        UUID.fromString(userId)
    } catch (e: IllegalArgumentException) {
        respond(HttpStatusCode.Unauthorized, ApiMessage(AppErrorCode.UNAUTHORIZED, "Invalid User ID format"))
        return null
    }

    if (!accessControlService.hasUser(userUuid)) {
        respond(
            HttpStatusCode.Unauthorized,
            ApiMessage(AppErrorCode.USER_NOT_FOUND, "User not found")
        )
        return null
    }

    if (accessControlService.isDeleted(userUuid)) {
        respond(
            HttpStatusCode.Unauthorized,
            ApiMessage(AppErrorCode.ACCOUNT_DELETED, "User account is deleted")
        )
        return null
    }

    if (!allowFrozenActions && isMutatingRequest()) {
        if (accessControlService.isAccountFrozen(userUuid)){
            respond(
                HttpStatusCode.Forbidden,
                ApiMessage(AppErrorCode.ACCOUNT_FROZEN, "Account is temporarily frozen")
            )
            return null
        }
    }

    return userId
}

/**
* Строгое получение таймзоны.
* Если хедера нет -> Ошибка 400.
*/
fun ApplicationCall.getTimezone(): String {
    val timezoneId = request.header("X-Timezone-Id")

    if (timezoneId.isNullOrBlank()) {
        throw LoyaltyException(
            AppErrorCode.INVALID_REQUEST,
            "Header 'X-Timezone-Id' is required"
        )
    }

    // Валидация: если прислали мусор ("Mars/City"),
    // лучше вернуть UTC, чем уронить сервер исключением Java Time.
    return try {
        ZoneId.of(timezoneId).id
    } catch (e: Exception) {
        "UTC" // Фоллбэк только на случай некорректного формата, но не отсутствия хедера
    }
}

/**
 * Получение валюты на основе таймзоны из хедера.
 */
fun ApplicationCall.getCurrencyForTimezone(): String {
    return TimezoneUtils.getCurrencyForTimezone(getTimezone())
}


/**
 * Получение кода страны на основе таймзоны из хедера.
 */
fun ApplicationCall.getCountryCodeForTimezone(): CountryCode {
    return TimezoneUtils.getCountryForTimezone(getTimezone())
}

/**
 * Извлекает язык.
 * Здесь строгость не нужна, фоллбэк на RU допустим.
 */
fun ApplicationCall.resolveLanguage(default: String = "ru"): String {
    val header = request.header(HttpHeaders.AcceptLanguage)
    // Берем первые 2 символа ("en-US" -> "en")
    return header?.take(2)?.lowercase() ?: default
}

/**
 * Извлекает ID текущего рабочего пространства из заголовка.
 * Это может быть ID Партнера (для Владельца/Менеджера) или ID Точки (для Кассира).
 */
fun ApplicationCall.getWorkspaceIdOrNull(): String? {
    return request.header("X-Workspace-Id")
}

fun ApplicationCall.getWorkspaceIdOrThrow(): String {
    return request.header("X-Workspace-Id") ?: throw LoyaltyException(AppErrorCode.WORKSPACE_ID_MISSING, "Workspace ID is required")
}

// Приватный хелпер

private fun ApplicationCall.isMutatingRequest(): Boolean {
    return request.httpMethod in setOf(
        HttpMethod.Post,
        HttpMethod.Put,
        HttpMethod.Delete,
        HttpMethod.Patch
    )
}

fun ApplicationCall.extractSignals(): VerificationSignals {
    return VerificationSignals(
        ip = request.header("X-Forwarded-For")?.split(",")?.firstOrNull() ?: request.local.remoteHost,
        deviceId = request.header("X-Device-Id"),
        platform = request.header("X-Device-Platform"),
        deviceModel = request.header("X-Device-Model"),
        osVersion = request.header("X-Os-Version"),
        appVersion = request.header("X-App-Version")
    )
}

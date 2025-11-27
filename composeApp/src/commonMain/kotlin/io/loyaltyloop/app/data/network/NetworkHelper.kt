package io.loyaltyloop.app.data.network

import co.touchlab.kermit.Logger
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.NetworkResult
import kotlinx.serialization.json.Json

val jsonParser = Json { ignoreUnknownKeys = true }
val logger = Logger.withTag("NetworkHelper")

val authCodes = setOf(
    AppErrorCode.UNAUTHORIZED,
    AppErrorCode.USER_NOT_FOUND,
    AppErrorCode.TOKEN_EXPIRED,
    AppErrorCode.INVALID_CODE,
    AppErrorCode.CODE_EXPIRED
)

suspend inline fun <reified T> safeNetworkCall(
    crossinline apiCall: suspend () -> HttpResponse
): NetworkResult<T> {
    return try {
        val response = apiCall()
        val status = response.status

        when (status.value) {
            in 200..299 -> {
                val data = response.body<T>()
                NetworkResult.Success(data)
            }
            in 400..499 -> {
                val rawError = response.bodyAsText()
                try {
                    val apiMessage = jsonParser.decodeFromString<ApiMessage>(rawError)

                    if (status == HttpStatusCode.Unauthorized && authCodes.contains(apiMessage.code)) {
                        AuthWatcher.onAuthError(apiMessage.code)
                    }

                    NetworkResult.Error(apiMessage.code, apiMessage.message)
                } catch (e: Exception) {
                    logger.write("Failed to parse error body: ${e.message}", LogType.Warning)

                    if (status == HttpStatusCode.Unauthorized) {
                        AuthWatcher.onUnauthorizedFallback()
                        NetworkResult.Error(AppErrorCode.UNAUTHORIZED, extractMessage(rawError))
                    } else {
                        NetworkResult.Failure(e)
                    }
                }
            }
            in 500..599 -> {
                NetworkResult.Error(AppErrorCode.INTERNAL_ERROR, "Server Error: ${status.value}")
            }
            else -> {
                NetworkResult.Failure(Exception("Unexpected Status: $status"))
            }
        }
    } catch (e: Exception) {
        logger.write("Network Call Failed", LogType.Error, e)
        NetworkResult.Failure(e)
    }
}

fun extractMessage(raw: String): String {
    return try {
        val msg = jsonParser.decodeFromString<ApiMessage>(raw).message
        msg?.takeIf { it.isNotBlank() } ?: raw
    } catch (e: Exception) {
        raw
    }
}

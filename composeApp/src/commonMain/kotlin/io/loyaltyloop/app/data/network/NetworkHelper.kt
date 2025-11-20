package io.loyaltyloop.app.data.network

import co.touchlab.kermit.Logger
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.errors.IOException
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.write

// Создаем логгер для этого файла
val logger = Logger.withTag("NetworkHelper")

suspend inline fun <reified T> safeApiCall(
    crossinline apiCall: suspend () -> HttpResponse
): Result<T> {
    return try {
        val response = apiCall()

        // Логируем не-200 ответы
        if (response.status.value !in 200..299) {
            logger.write("Non-success response: ${response.status}", LogType.Warning)
        }

        when (response.status.value) {
            in 200..299 -> {
                val data = response.body<T>()
                Result.success(data)
            }
            401 -> Result.failure(UnauthorizedException())
            in 400..499 -> {
                val errorText = response.bodyAsText()
                Result.failure(ClientException(errorText))
            }
            in 500..599 -> Result.failure(ServerException(response.status.value))
            else -> Result.failure(UnknownException(Exception("Status: ${response.status.value}")))
        }
    } catch (e: Exception) {
        logger.write("API Call Failed", LogType.Error, e)

        if (e is IOException) {
            Result.failure(NetworkException())
        } else {
            Result.failure(UnknownException(e))
        }
    }
}
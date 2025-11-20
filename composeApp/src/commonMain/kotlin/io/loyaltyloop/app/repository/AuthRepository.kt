package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.SendCodeRequest
import io.loyaltyloop.shared.models.UserDto

class AuthRepository(private val client: HttpClient) {

    // 1. Отправка кода (Возвращает debugCode для теста или пустую строку)
    suspend fun sendCode(phone: String): Result<String> {
        return try {
            val response = client.post("/auth/send-code") {
                contentType(ContentType.Application.Json)
                setBody(SendCodeRequest(phone))
            }

            if (response.status == HttpStatusCode.OK) {
                // Сервер возвращает Map<String, String>: {"status": "Code sent", "debugCode": "1234"}
                val responseBody = response.body<Map<String, String>>()
                val debugCode = responseBody["debugCode"] ?: ""

                Result.success(debugCode)
            } else {
                Result.failure(Exception("Ошибка сервера: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(phone: String, code: String): Result<AuthResponse> {
        return try {
            val request = io.loyaltyloop.shared.models.VerifyCodeRequest(phone, code)

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.value in 200..299) {
                // В реальности мы тут сохраним токен
                Result.success(response.body<AuthResponse>())
            } else {
                Result.failure(Exception("Неверный код"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.loyaltyloop.shared.models.UserDto

class AuthRepository(private val client: HttpClient) {

    // Функция регистрации
    suspend fun register(user: UserDto): String {
        try {
            // Отправляем POST запрос на /auth/register
            // Базовый URL (localhost:8080) уже настроен в DI
            val response = client.post("/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(user)
            }

            return "Успех: ${response.body<String>()}"
        } catch (e: Exception) {
            return "Ошибка: ${e.message}"
        }
    }

    suspend fun login(phone: String, code: String): Result<String> {
        return try {
            val request = io.loyaltyloop.shared.models.VerifyCodeRequest(phone, code)

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status.value in 200..299) {
                // В реальности мы тут сохраним токен
                Result.success("Успешный вход!")
            } else {
                Result.failure(Exception("Неверный код"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
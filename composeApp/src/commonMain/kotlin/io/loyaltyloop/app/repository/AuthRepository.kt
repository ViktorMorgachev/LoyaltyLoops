package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.loyaltyloop.app.data.network.safeApiCall
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.SendCodeRequest
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.UserProfileResponse
import io.loyaltyloop.shared.models.VerifyCodeRequest

class AuthRepository(private val client: HttpClient) {

    // 1. Отправка кода
    suspend fun sendCode(phone: String): Result<String> {
        return safeApiCall<Map<String, String>> {
            client.post("/auth/send-code") {
                contentType(ContentType.Application.Json)
                setBody(SendCodeRequest(phone))
            }
        }.map { it["debugCode"] ?: "" } // Трансформируем Map в String
    }

    // 2. Логин
    suspend fun login(phone: String, code: String): Result<AuthResponse> {
        return safeApiCall<AuthResponse> {
            client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(VerifyCodeRequest(phone, code))
            }
        }
    }

    // 3. Профиль (Check Session)
    suspend fun getProfile(): Result<UserProfileResponse> {
        return safeApiCall<UserProfileResponse> {
            client.get("/client/me")
        }
    }

    suspend fun updateProfile(
        firstName: String,
        lastName: String? = null,
        email: String? = null
    ): Result<String> {
        // Формируем запрос
        val request = UpdateProfileRequest(
            firstName = firstName,
            lastName = lastName?.ifBlank { null },
            email = email?.ifBlank { null }
        )

        // Мы ожидаем ApiMessage, но нам интересен просто успех (Result.success)
        // Можно мапить в String message
        return safeApiCall<io.loyaltyloop.shared.models.ApiMessage> {
            client.post("/client/profile") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.map { it.message }
    }
}
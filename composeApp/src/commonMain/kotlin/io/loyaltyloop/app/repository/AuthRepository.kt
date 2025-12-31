package io.loyaltyloop.app.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.loyaltyloop.app.data.network.safeNetworkCall
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.AppErrorCode
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.NetworkResult
import io.loyaltyloop.shared.models.SendCodeRequest
import io.loyaltyloop.shared.models.TestApiErrors
import io.loyaltyloop.shared.models.UpdateLanguageRequest
import io.loyaltyloop.shared.models.UpdateProfileRequest
import io.loyaltyloop.shared.models.UserProfileResponse
import io.loyaltyloop.shared.models.VerifyCodeRequest
import io.loyaltyloop.shared.models.TelegramAuthStartResponse
import io.loyaltyloop.shared.models.AuthSessionStatusResponse
import io.loyaltyloop.shared.models.PrecheckRequest

class AuthRepository(private val client: HttpClient) {

    // Telegram Auth
    suspend fun startTelegramAuth(): NetworkResult<TelegramAuthStartResponse> {
        return safeNetworkCall {
            client.post("/auth/telegram/start")
        }
    }

    suspend fun checkTelegramStatus(uuid: String): NetworkResult<AuthSessionStatusResponse> {
        return safeNetworkCall {
            client.get("/auth/telegram/status/$uuid")
        }
    }

    // 1. Отправка кода
    suspend fun sendCode(phone: String): NetworkResult<Map<String, String>> {
        return safeNetworkCall {
            client.post("/auth/send-code") {
                contentType(ContentType.Application.Json)
                setBody(SendCodeRequest(phone))
            }
        }
    }

    suspend fun precheck(phone: String): NetworkResult<ApiMessage> {
        return safeNetworkCall {
            client.post("/auth/precheck") {
                contentType(ContentType.Application.Json)
                setBody(PrecheckRequest(phone))
            }
        }
    }

    // 2. Логин
    suspend fun login(phone: String, code: String): NetworkResult<AuthResponse> {
        return safeNetworkCall {
            client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(VerifyCodeRequest(phone, code))
            }
        }
    }


    suspend fun getProfile(): NetworkResult<UserProfileResponse> {
        return safeNetworkCall {
            client.get("/client/me")
        }
    }

    suspend fun testApiHandling(error: AppErrorCode): NetworkResult<TestApiErrors> {
        return safeNetworkCall {
            client.post("/client/test"){
                contentType(ContentType.Application.Json)
                setBody(TestApiErrors(apiError = error))
            }
        }
    }

    suspend fun updateProfile(
        firstName: String,
        lastName: String? = null,
        email: String? = null
    ): NetworkResult<ApiMessage> {
        return safeNetworkCall {
            client.post("/client/profile") {
                contentType(ContentType.Application.Json)
                setBody(UpdateProfileRequest(
                    firstName = firstName,
                    lastName = lastName?.ifBlank { null },
                    email = email?.ifBlank { null }
                ))
            }
        }
    }

    suspend fun updateLanguage(languageCode: String): NetworkResult<ApiMessage> {
        return safeNetworkCall {
            client.post("/client/language") {
                contentType(ContentType.Application.Json)
                setBody(UpdateLanguageRequest(languageCode))
            }
        }
    }

    suspend fun requestAccountDeletion(): NetworkResult<io.loyaltyloop.shared.models.RequestAccountDeletionResponse> {
        return safeNetworkCall {
            client.post("/auth/account/delete/request")
        }
    }

    suspend fun confirmAccountDeletion(code: String, reason: String): NetworkResult<Unit> {
        return safeNetworkCall {
            client.post("/auth/account/delete/confirm") {
                contentType(ContentType.Application.Json)
                setBody(io.loyaltyloop.shared.models.ConfirmAccountDeletionRequest(code, reason))
            }
        }
    }
}

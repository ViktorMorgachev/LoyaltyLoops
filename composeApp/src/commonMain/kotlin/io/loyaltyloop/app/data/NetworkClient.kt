package io.loyaltyloop.app.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.loyaltyloop.app.config.SERVER_URL
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.RefreshTokenRequest
import kotlinx.serialization.json.Json

object NetworkClient {

    fun create(engine: HttpClientEngine, tokenStorage: TokenStorage): HttpClient {
        return HttpClient(engine) {

            // 1. JSON
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            // 2. LOGS
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.ALL
            }

            // 3. AUTH MAGIC (Автоматический рефреш)
            install(Auth) {
                bearer {
                    // А. Загрузка токена из памяти при старте запроса
                    loadTokens {
                        val access = tokenStorage.getAccessToken()
                        val refresh = tokenStorage.getRefreshToken()
                        if (access != null && refresh != null) {
                            BearerTokens(access, refresh)
                        } else {
                            null
                        }
                    }

                    // Б. Логика обновления, если пришел 401
                    refreshTokens {
                        val oldTokens = oldTokens
                        // Если старого токена не было - нечего обновлять
                        val refreshToken = oldTokens?.refreshToken ?: return@refreshTokens null

                        try {
                            // Делаем запрос на /refresh
                            // Важно: используем `client` из скоупа, он знает базовый URL
                            val response = client.post("/auth/refresh") {
                                contentType(ContentType.Application.Json)
                                setBody(RefreshTokenRequest(refreshToken))
                                markAsRefreshTokenRequest() // <-- Не перехватывать этот запрос!
                            }

                            if (response.status == HttpStatusCode.OK) {
                                // Успех! Сохраняем новые
                                val newAuth = response.body<AuthResponse>()
                                tokenStorage.saveAuthData(
                                    newAuth.accessToken,
                                    newAuth.refreshToken,
                                    newAuth.userId
                                )
                                BearerTokens(newAuth.accessToken, newAuth.refreshToken)
                            } else {
                                // Рефреш протух - разлогин
                                tokenStorage.clear()
                                null
                            }
                        } catch (e: Exception) {
                            // Ошибка сети при рефреше
                            null
                        }
                    }
                }
            }

            // 4. URL
            defaultRequest {
                url(SERVER_URL)
            }
        }
    }
}
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
import io.ktor.client.request.header
import co.touchlab.kermit.Logger as KermitLogger
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.loyaltyloop.app.config.SERVER_URL
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.RefreshTokenRequest
import kotlinx.serialization.json.Json


object NetworkClient {

    private val netLog = KermitLogger.withTag("LoyaltyNetwork")

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
                // Связываем Ktor с Kermit
                logger = object : io.ktor.client.plugins.logging.Logger {
                    override fun log(message: String) {
                        netLog.write(message)
                    }
                }
                level = LogLevel.ALL
            }

            install(Auth) {
                bearer {
                    // А. Загрузка токена из памяти при старте запроса
                    loadTokens {


                        val access = tokenStorage.getAccessToken()
                        val refresh = tokenStorage.getRefreshToken()

                        netLog.d { "🔐 Auth Plugin: Loading tokens..." }
                        netLog.d { "   -> Access: ${access?.take(5)}..." }
                        netLog.d { "   -> Refresh: ${refresh?.take(5)}..." }

                        if (access != null && refresh != null) {
                            BearerTokens(access, refresh)
                        } else {
                            netLog.w { "🔐 Auth Plugin: No tokens found! Request will be anonymous." }
                            null
                        }
                    }

                    // Б. Логика обновления, если пришел 401
                    refreshTokens {
                        val oldTokens = oldTokens
                        // Если старого токена не было - нечего обновлять
                        val refreshToken = oldTokens?.refreshToken ?: return@refreshTokens null
                        netLog.write("Attempting to refresh token...", LogType.Debug)

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
                                netLog.write("Token refreshed successfully")
                                val newAuth = response.body<AuthResponse>()
                                tokenStorage.saveAuthData(
                                    newAuth.accessToken,
                                    newAuth.refreshToken,
                                    newAuth.userId
                                )
                                BearerTokens(newAuth.accessToken, newAuth.refreshToken)
                            } else {
                                // Рефреш протух - разлогин
                                netLog.write("Refresh failed: ${response.status}", LogType.Warning)
                                tokenStorage.clear()
                                null
                            }
                        } catch (e: Exception) {
                            // Ошибка сети при рефреше
                            netLog.write("Network error during refresh", LogType.Error, e)
                            null
                        }
                    }
                }
            }

            // 4. URL
            defaultRequest {
                url(SERVER_URL)
                header(HttpHeaders.AcceptLanguage, "ru")
            }
        }
    }
}
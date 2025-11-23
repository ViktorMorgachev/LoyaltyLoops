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
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import io.loyaltyloop.app.config.SERVER_URL
import io.loyaltyloop.app.data.network.jsonParser
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.write
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.RefreshTokenRequest
import kotlinx.serialization.json.Json


object NetworkClient {

    private val netLog = KermitLogger.withTag("LoyaltyNetwork")

    fun create(engine: HttpClientEngine, tokenStorage: TokenStorage,  sessionManager: SessionManager): HttpClient {
        return HttpClient(engine) {
            // 1. JSON
            install(ContentNegotiation) {
                json(jsonParser)
            }

            // 2. LOGS
            install(Logging) {
                // Связываем Ktor с Kermit
                logger = object : Logger {
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
                        val accessToken = tokenStorage.getAccessToken()
                        val refreshToken = tokenStorage.getRefreshToken()

                        netLog.d {
                            """
                            🔐 AUTH PLUGIN: Loading tokens state...
                               -> Access: ${if (accessToken != null) "OK (${accessToken.takeLast(8)})" else "NULL"}
                               -> Refresh: ${if (refreshToken != null) "OK" else "NULL"}
                            """.trimIndent()
                        }

                        BearerTokens(accessToken = accessToken.orEmpty(), refreshToken = refreshToken.orEmpty())
                    }

                    sendWithoutRequest {  !it.url.pathSegments.contains("auth") }

                    // Б. Логика обновления, если пришел 401
                    refreshTokens {
                        val refreshToken = tokenStorage.getRefreshToken().orEmpty()

                        netLog.write("Attempting to refresh token...${refreshToken.takeLast(8)}", LogType.Debug)

                        try {
                            // Делаем запрос на /refresh
                            // Важно: используем `client` из скоупа, он знает базовый URL
                            val response = client.post("/auth/refresh") {
                                contentType(ContentType.Application.Json)
                                setBody(RefreshTokenRequest(refreshToken))
                                markAsRefreshTokenRequest()
                            }

                            if (response.status == HttpStatusCode.OK) {
                                // Успех! Сохраняем новые
                                netLog.write("Token refreshed successfully")
                                val newAuth = response.body<AuthResponse>()
                                tokenStorage.saveAuthData(
                                    accessToken = newAuth.accessToken,
                                    refreshToken = newAuth.refreshToken,
                                    userId = newAuth.userId,
                                    qrSecret = newAuth.qrSecret
                                )
                                BearerTokens(newAuth.accessToken, newAuth.refreshToken)
                            } else {
                                // Рефреш протух - разлогин
                                netLog.write("⛔ Refresh failed: ${response.status}. Logging out...", LogType.Warning)
                                sessionManager.logout()
                                null
                            }
                        } catch (e: Exception) {
                            // Ошибка сети при рефреше
                            netLog.write("💥 Network error during refresh", LogType.Error, e)
                            null
                        }
                    }
                }
            }

            // 4. URL
            defaultRequest {
                url(SERVER_URL)
                // TODO: Локаль добить функционал (думаю по умолчанию при старте приложения устанавливать локаль с
                //  системы но и давать сменить локаль в личном профиле и тут его слать а так же при получении профиля
                //  устанавливать локаль с его профиля и добавить флаг для реализации (isNotSetLocale чтобы понимать откуда брать язык (с системы или с профиля)
                header(HttpHeaders.AcceptLanguage, "ru")
            }
        }
    }
}
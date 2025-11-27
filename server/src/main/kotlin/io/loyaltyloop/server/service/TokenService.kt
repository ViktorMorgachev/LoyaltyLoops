package io.loyaltyloop.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.*
import io.loyaltyloop.shared.models.UserDto
import java.util.*

class TokenService(config: ApplicationConfig) {
    private val secret = config.property("jwt.secret").getString()
    private val issuer = config.property("jwt.issuer").getString()
    private val audience = config.property("jwt.audience").getString()

    private val accessLifetime = config.property("jwt.accessLifetime").getString().toLong()
    val refreshLifetime = config.property("jwt.refreshLifetime").getString().toLong()

    fun generateQrSecret(): String {
        // Просто случайная строка из 32 символов
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }

    fun generateTokens(user: UserDto): Pair<String, String> {
        val accessToken = JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("id", user.id)
            .withJWTId(UUID.randomUUID().toString())
            .withExpiresAt(Date(System.currentTimeMillis() + accessLifetime))
            .sign(Algorithm.HMAC256(secret))

        val refreshToken = JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("id", user.id)
            .withJWTId(UUID.randomUUID().toString())
            // Refresh токен живет дольше
            .withExpiresAt(Date(System.currentTimeMillis() + refreshLifetime))
            .sign(Algorithm.HMAC256(secret))

        return Pair(accessToken, refreshToken)
    }

    // Валидация Refresh токена (проверяем, не истек ли он)
    fun validateRefreshToken(token: String): String? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(secret))
                .withIssuer(issuer)
                .build()

            val decoded = verifier.verify(token)

            // Возвращаем ID пользователя из токена
            decoded.getClaim("id").asString()
        } catch (e: Exception) {
            null // Токен подделан или протух
        }
    }

    fun validateAccessToken(token: String): String? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(secret))
                .withAudience(audience)
                .withIssuer(issuer)
                .build()
            val decoded = verifier.verify(token)
            decoded.getClaim("id").asString()
        } catch (e: Exception) {
            null
        }
    }
}
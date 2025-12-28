package io.loyaltyloop.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.config.*
import io.loyaltyloop.shared.models.UserDto
import java.util.*

// TODO checked
class TokenService(config: ApplicationConfig) {
    private val secret = config.property("jwt.secret").getString()
    private val issuer = config.property("jwt.issuer").getString()
    private val audience = config.property("jwt.audience").getString()
    private val accessLifetime = config.property("jwt.accessLifetime").getString().toLong()
    val refreshLifetime = config.property("jwt.refreshLifetime").getString().toLong()

    fun generateQrSecret(): String {
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
            .withExpiresAt(Date(System.currentTimeMillis() + refreshLifetime))
            .sign(Algorithm.HMAC256(secret))

        return Pair(accessToken, refreshToken)
    }

    fun validateRefreshToken(token: String): String? {
        return try {
            val verifier = JWT.require(Algorithm.HMAC256(secret))
                .withAudience(audience)
                .withIssuer(issuer)
                .build()

            val decoded = verifier.verify(token)

            decoded.getClaim("id").asString()
        } catch (_: Exception) {
            null
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
        } catch (_: Exception) {
            null
        }
    }
}
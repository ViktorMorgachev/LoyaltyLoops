package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.RefreshTokenRequest
import io.loyaltyloop.shared.models.SendCodeRequest
import io.loyaltyloop.shared.models.VerifyCodeRequest
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthTest {

    private fun generateExpiredToken(userId: String): String {
        return JWT.create()
            .withAudience("http://test-server/hello")
            .withIssuer("http://test-server/")
            .withClaim("id", userId)
            .withExpiresAt(Date(System.currentTimeMillis() - 3_600_000))
            .sign(Algorithm.HMAC256("test_secret_key_12345"))
    }

    @Test
    fun otpLoginFlow_returnsTokens() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val phone = generateValidPhone()

        val sendCodeResponse = client.post("/auth/send-code") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.AcceptLanguage, "ru")
            setBody(SendCodeRequest(phone))
        }
        assertEquals(HttpStatusCode.OK, sendCodeResponse.status)
        val otpCode = extractOtpCode(sendCodeResponse)
        assertTrue(otpCode.isNotBlank(), "OTP code must be returned in debug payload for tests")

        val loginResponse = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.AcceptLanguage, "ru")
            header("X-Timezone-Id", "Asia/Bishkek")
            setBody(VerifyCodeRequest(phone, otpCode))
        }
        assertEquals(HttpStatusCode.OK, loginResponse.status)

        val auth = loginResponse.body<AuthResponse>()
        assertTrue(auth.isNewUser)
        assertTrue(auth.accessToken.isNotBlank())
        assertTrue(auth.refreshToken.isNotBlank())
        assertTrue(auth.qrSecret.isNotBlank())
        assertTrue(auth.workspaces.isEmpty(), "New user should not have workspaces yet")
    }

    @Test
    fun refreshToken_rotatesAndRevokesPreviousOne() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val initial = client.registerAndLogin()

        val refreshResponse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(initial.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshResponse.status)

        val refreshed = refreshResponse.body<AuthResponse>()
        assertEquals(initial.userId, refreshed.userId)
        assertNotEquals(initial.accessToken, refreshed.accessToken)
        assertNotEquals(initial.refreshToken, refreshed.refreshToken)

        val reusedResponse = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(initial.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, reusedResponse.status)
    }

    @Test
    fun expiredAccessToken_isRejected() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val expiredAccess = generateExpiredToken("ghost-user")

        val response = client.get("/client/me") {
            header("Authorization", "Bearer $expiredAccess")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Token is invalid or expired", response.bodyAsText())
    }
}

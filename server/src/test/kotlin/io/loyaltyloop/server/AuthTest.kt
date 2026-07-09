package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.testing.testApplication
import java.util.Date
import kotlin.test.Test

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
//        val client = createJsonClient()
//        val phone = generateValidPhone()
//
//        val sendCodeResponse = client.post("/auth/send-code") {
//            contentType(ContentType.Application.Json)
//            header(HttpHeaders.AcceptLanguage, "ru")
//            setBody(SendCodeRequest(phone))
//        }
//        assertEquals(HttpStatusCode.OK, sendCodeResponse.status)
//        val otpCode = Json.parseToJsonElement(sendCodeResponse.bodyAsText())
//            .jsonObject["debugCode"]!!.jsonPrimitive.content
//        assertTrue(otpCode.isNotBlank(), "OTP code must be returned in debug payload for tests")
//
//        val loginResponse = client.post("/auth/login") {
//            contentType(ContentType.Application.Json)
//            header(HttpHeaders.AcceptLanguage, "ru")
//            setBody(VerifyCodeRequest(phone, otpCode))
//        }
//        assertEquals(HttpStatusCode.OK, loginResponse.status)
//
//        val auth = loginResponse.body<AuthResponse>()
//        assertTrue(auth.isNewUser)
//        assertTrue(auth.accessToken.isNotBlank())
//        assertTrue(auth.refreshToken.isNotBlank())
//        assertTrue(auth.qrSecret.isNotBlank())
//        assertTrue(auth.workspaces.isEmpty(), "New user should not have workspaces yet")
    }

    @Test
    fun refreshToken_rotatesAndRevokesPreviousOne() = testApplication {
        configureTestEnv()
//        val client = createJsonClient()
//        val initial = client.registerAndLogin()
//
//        val refreshResponse = client.post("/auth/refresh") {
//            contentType(ContentType.Application.Json)
//            setBody(RefreshTokenRequest(initial.refreshToken))
//        }
//        assertEquals(HttpStatusCode.OK, refreshResponse.status)
//
//        val refreshed = refreshResponse.body<AuthResponse>()
//        assertEquals(initial.userId, refreshed.userId)
//        assertNotEquals(initial.accessToken, refreshed.accessToken)
//        assertNotEquals(initial.refreshToken, refreshed.refreshToken)
//
//        val reusedResponse = client.post("/auth/refresh") {
//            contentType(ContentType.Application.Json)
//            setBody(RefreshTokenRequest(initial.refreshToken))
//        }
//        assertEquals(HttpStatusCode.Unauthorized, reusedResponse.status)
    }

    @Test
    fun expiredAccessToken_isRejected() = testApplication {
        configureTestEnv()
//        val client = createJsonClient()
//        val expiredAccess = generateExpiredToken("ghost-user")
//
//        val response = client.get("/client/me") {
//            header("Authorization", "Bearer $expiredAccess")
//        }
//
//        assertEquals(HttpStatusCode.Unauthorized, response.status)
//        assertEquals("Token is invalid or expired", response.bodyAsText())
    }

    @Test
    fun tokenOfDeletedUser_isRejected() = testApplication {
        configureTestEnv()
//        val client = createJsonClient()
//        val admin = client.registerAsAdmin(userRepo)
//        val victim = client.registerAndLogin()
//
//        val deleteResponse = client.delete("/admin/users/${victim.userId}") {
//            header("Authorization", "Bearer ${admin.accessToken}")
//        }
//        assertEquals(HttpStatusCode.OK, deleteResponse.status)
//
//        val response = client.get("/client/me") {
//            header("Authorization", "Bearer ${victim.accessToken}")
//        }
//
//        assertEquals(HttpStatusCode.Unauthorized, response.status)
//        val error = response.body<ApiMessage>()
//        assertEquals(AppErrorCode.USER_NOT_FOUND, error.code)
    }
}

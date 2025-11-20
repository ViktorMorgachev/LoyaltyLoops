package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.loyaltyloop.shared.models.*
import java.util.*
import kotlin.test.*

class AuthTest {

    // Создаем протухший токен вручную
    private fun generateExpiredToken(userId: String): String {
        return JWT.create()
            .withAudience("http://test-server/hello")
            .withIssuer("http://test-server/")
            .withClaim("id", userId)
            .withExpiresAt(Date(System.currentTimeMillis() - 3600000)) // -1 час
            .sign(Algorithm.HMAC256("test_secret_key_12345"))
    }

    // --- ТЕСТЫ ---

    @Test
    fun test01_RegisterNewUser() = testApplication {
        configureTestEnv()
        val client = createJsonClient()

        val authResponse = client.registerAndLogin() // Генерирует номер сама

        // Проверки
        assertNotNull(authResponse.accessToken)
        assertTrue(authResponse.isNewUser)
    }

    @Test
    fun test02_LoginExistingUser() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val phone = generateValidPhone()

        // 1. Первая регистрация (через хелпер)
        val firstAuth = client.registerAndLogin(phone = phone)

        // 2. Повторный вход (приходится делать руками, чтобы проверить isNewUser=false,
        // либо можно вызвать хелпер второй раз с тем же номером)
        val secondAuth = client.registerAndLogin(phone = phone)

        // Проверки
        assertFalse(secondAuth.isNewUser)
        assertEquals(firstAuth.userId, secondAuth.userId)
    }

    @Test
    fun test03_RefreshTokenSuccess() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val authResponse = client.registerAndLogin()

        // 2. REFRESH
        val refreshRes = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(authResponse.refreshToken))
        }

        if (refreshRes.status != HttpStatusCode.OK) {
            val errorText = refreshRes.bodyAsText()
            println("Refresh Failed Error: $errorText") // Увидим причину (Invalid Token или User not found)
            fail("Refresh returned ${refreshRes.status}: $errorText")
        }

        val tokens2 = refreshRes.body<AuthResponse>()

        assertNotEquals(authResponse.accessToken, tokens2.accessToken)
        assertNotEquals(authResponse.refreshToken, tokens2.refreshToken)
        assertEquals(authResponse.userId, tokens2.userId)
    }

    @Test
    fun test04_RefreshTokenReuseProtection() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val authResponse = client.registerAndLogin()

        val originalRefreshToken = authResponse.refreshToken

        // 2. Первое обновление (Легальное)
        val refreshRes1 = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(originalRefreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshRes1.status)

        kotlinx.coroutines.delay(200)

        // 3. Второе обновление ТЕМ ЖЕ токеном (Атака)
        val refreshRes2 = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(originalRefreshToken))
        }

        assertEquals(HttpStatusCode.Unauthorized, refreshRes2.status)
    }

    @Test
    fun test05_ExpiredAccessToken() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val expiredAccess = generateExpiredToken("some_user_id")

        val response = client.get("/auth/me") {
            header("Authorization", "Bearer $expiredAccess")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Token is invalid or expired", response.bodyAsText())
    }

    @Test
    fun test06_ExpiredRefreshToken() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val expiredRefresh = generateExpiredToken("some_user_id")

        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(expiredRefresh))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun test06_LanguageAutoUpdate() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val phone = generateValidPhone()

        // 1. Регистрируемся как RU (через хелпер)
        val authRu = client.registerAndLogin(phone = phone, language = "ru")

        // 2. Логинимся снова как EN (через хелпер)
        client.registerAndLogin(phone = phone, language = "en")

        // 3. Проверяем БД (запрос через токен, чтобы было честно)
        val meRes = client.get("/auth/me") {
            header("Authorization", "Bearer ${authRu.accessToken}")
        }
        val profile = meRes.body<UserProfileResponse>()

        assertEquals("en", profile.language, "Язык должен обновиться на EN")
    }
}
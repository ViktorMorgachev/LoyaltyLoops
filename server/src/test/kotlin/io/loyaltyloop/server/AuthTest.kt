package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.loyaltyloop.shared.models.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.*
import kotlin.test.*

class AuthTest {

    // --- Хелперы для тестов ---

    // Парсим "debugCode" из ответа (так как пока возвращаем Map, а не DTO)
    private suspend fun extractOtpCode(response: HttpResponse): String {
        val text = response.bodyAsText()
        println("Server Response: $text") // Логируем, чтобы видеть глазами

        // Парсим JSON по-настоящему
        val jsonObject = Json.parseToJsonElement(text).jsonObject
        return jsonObject["debugCode"]?.jsonPrimitive?.content ?: ""
    }

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
    fun test01_LoginWithDynamicOtp() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val phone = generateValidPhone()

        // 1. Запрашиваем код
        val sendRes = client.post("/auth/send-code") {
            contentType(ContentType.Application.Json)
            setBody(SendCodeRequest(phone))
        }
        assertEquals(HttpStatusCode.OK, sendRes.status)

        // 2. Достаем код (теперь надежно)
        val code = extractOtpCode(sendRes)
        // Если код пустой, значит парсинг не удался
        assertTrue(code.length == 4, "Код должен быть 4-значным, получили: '$code'")

        // 3. Логинимся
        val loginRes = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(VerifyCodeRequest(phone = phone, code = code))
        }

        // Если статус не OK, выводим ошибку, чтобы понять почему
        if (loginRes.status != HttpStatusCode.OK) {
            fail("Login failed with status ${loginRes.status}: ${loginRes.bodyAsText()}")
        }

        val authResponse = loginRes.body<AuthResponse>()
        assertNotNull(authResponse.accessToken)
        assertTrue(authResponse.isNewUser)
    }

    @Test
    fun test02_RefreshTokenSuccess() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val phone = generateValidPhone()

        // 1. Логин
        val sendRes = client.post("/auth/send-code") {
            contentType(ContentType.Application.Json)
            setBody(SendCodeRequest(phone))
        }
        val code = extractOtpCode(sendRes)

        val loginRes = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(VerifyCodeRequest(phone, code))
        }

        if (loginRes.status != HttpStatusCode.OK) fail("Login failed: ${loginRes.bodyAsText()}")

        val tokens1 = loginRes.body<AuthResponse>()

        // 2. REFRESH
        val refreshRes = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(tokens1.refreshToken))
        }

        if (refreshRes.status != HttpStatusCode.OK) {
            val errorText = refreshRes.bodyAsText()
            println("Refresh Failed Error: $errorText") // Увидим причину (Invalid Token или User not found)
            fail("Refresh returned ${refreshRes.status}: $errorText")
        }

        val tokens2 = refreshRes.body<AuthResponse>()

        assertNotEquals(tokens1.accessToken, tokens2.accessToken)
        assertNotEquals(tokens1.refreshToken, tokens2.refreshToken)
        assertEquals(tokens1.userId, tokens2.userId)
    }

    @Test
    fun test03_RefreshTokenReuseProtection() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val phone = generateValidPhone()

        // 1. Логин
        val sendRes = client.post("/auth/send-code") {
            contentType(ContentType.Application.Json)
            setBody(SendCodeRequest(phone))
        }
        val code = extractOtpCode(sendRes)
        val loginRes = client.post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(VerifyCodeRequest(phone, code))
        }

        if (loginRes.status != HttpStatusCode.OK) fail("Login failed")

        val originalRefreshToken = loginRes.body<AuthResponse>().refreshToken

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
    fun test04_ExpiredAccessToken() = testApplication {
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
    fun test05_ExpiredRefreshToken() = testApplication {
        configureTestEnv()
        val client = createJsonClient()
        val expiredRefresh = generateExpiredToken("some_user_id")

        val response = client.post("/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(expiredRefresh))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}
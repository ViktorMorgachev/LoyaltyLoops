package io.loyaltyloop.server

import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.PlatformRepository
import io.loyaltyloop.shared.models.AuthResponse
import io.loyaltyloop.shared.models.ChangePointStatusRequest
import io.loyaltyloop.shared.models.CreateTradingPointRequest
import io.loyaltyloop.shared.models.Currency
import io.loyaltyloop.shared.models.JoinTradingPointRequest
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.ProcessTransactionRequest
import io.loyaltyloop.shared.models.SendCodeRequest
import io.loyaltyloop.shared.models.TradingPointType
import io.loyaltyloop.shared.models.TransactionResult
import io.loyaltyloop.shared.models.TransactionStrategy
import io.loyaltyloop.shared.models.UserRole
import io.loyaltyloop.shared.models.VerifyCodeRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals

/**
 * Общая конфигурация для всех тестов (База H2 + Настройки JWT)
 */
fun ApplicationTestBuilder.configureTestEnv() {
    environment {
        config = MapApplicationConfig(
            // --- DATABASE (H2 In-Memory) ---
            "storage.driverClassName" to "org.h2.Driver",
            "storage.jdbcUrl" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "storage.user" to "root",
            "storage.password" to "",

            // --- JWT (Test Keys) ---
            "jwt.secret" to "test_secret_key_12345",
            "jwt.issuer" to "http://test-server/",
            "jwt.audience" to "http://test-server/hello",
            "jwt.realm" to "Test Realm",
            "jwt.accessLifetime" to "3600000",  // 1 час
            "jwt.refreshLifetime" to "86400000", // 1 день

            "features.enableTestSupport" to "true",
            "admin.defaultPin" to "0000",
            "platform.inviteCode" to "SECRET123",
            "platform.managerInviteCode" to "MANAGER123"
        )
    }

    // Автоматически запускаем модуль приложения
    application {
        module()
    }
}


suspend fun HttpClient.fetchCard(
    customerToken: String,
    cardId: String
): LoyaltyCardDto {
    val cards = get("/client/cards") {
        header("Authorization", "Bearer $customerToken")
    }.body<List<LoyaltyCardDto>>()

    return cards.first { it.id == cardId }
}

/**
 * Создает готовый HTTP-клиент с поддержкой JSON
 */
fun ApplicationTestBuilder.createJsonClient(): HttpClient {
    return createClient {
        install(ContentNegotiation) {
            json()
        }
    }
}

/**
 * Генерирует валидный случайный номер KG (+996 555 XX XX XX)
 */
fun generateValidPhone(): String {
    // Берем последние 6 цифр от текущего времени, чтобы обеспечить уникальность
    val uniquePart = System.currentTimeMillis().toString().takeLast(6)
    return "+996555$uniquePart"
}

suspend fun HttpClient.registerAndLogin(
    phone: String = generateValidPhone(), // Можно передать свой номер или сгенерировать
    language: String = "ru",               // Язык для хедера
    testDescr: String = "registerAndLogin",
    withLogs: Boolean = true
): AuthResponse {

    if (withLogs) println("${testDescr}: Запрос смс")

    val sendRes = post("/auth/send-code") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.AcceptLanguage, language)
        setBody(SendCodeRequest(phone))
    }

    if (sendRes.status != HttpStatusCode.OK) {
        throw IllegalStateException("Test setup failed: Send Code returned ${sendRes.status}")
    }
    if (withLogs)  println("${testDescr}: Запрос смс: Успех")
    val code = extractOtpCode(sendRes)

    // 2. Логин
    if (withLogs)  println("${testDescr}: Логин")
    val loginRes = post("/auth/login") {
        contentType(ContentType.Application.Json)
        header(HttpHeaders.AcceptLanguage, language)
        setBody(VerifyCodeRequest(phone, code))
    }

    if (loginRes.status != HttpStatusCode.OK) {
        throw IllegalStateException("Test setup failed: Login returned ${loginRes.status} body: ${loginRes.bodyAsText()}")
    }

    if (withLogs)  println("${testDescr}: Логин: Успех")

    return loginRes.body()
}

/**
 * Достает код из ответа сервера (для MVP)
 */
suspend fun extractOtpCode(response: HttpResponse): String {
    val text = response.bodyAsText()
    val jsonObject = Json.parseToJsonElement(text).jsonObject
    return jsonObject["debugCode"]?.jsonPrimitive?.content ?: ""
}

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

/**
 * Хелпер: Создает полную экосистему (Партнер -> Точка) и регистрирует там Кассира.
 * Возвращает токен Кассира.
 */
suspend fun HttpClient.createCashierEcosystem(
    ownerToken: String,
    ownerId: String,
    partnerRepo: PartnerRepository,
    testDescr: String = "createCashierEcosystem"
): String { // Возвращает cashierToken

    // 1. Создаем точку
    // (Мы не можем достать inviteCode через API создания, поэтому лезем в базу или делаем getPoints)
    // Упростим: Создадим точку через API, потом найдем её в БД
    createTradingPoint(ownerToken = ownerToken, name = "Cashier Point", currency = Currency.KGS, type =  TradingPointType.COFFEE_SHOP)

    val partnerId = partnerRepo.getPartnerByIdQ(ownerId).id
    val point = partnerRepo.getPointsByPartnerId(partnerId).first()
    val inviteCode = point.inviteCode ?: throw IllegalStateException("No invite code generated")

    // 2. Регистрируем нового юзера (будущего кассира)
    val cashierAuth = registerAndLogin(phone = generateValidPhone(), testDescr = testDescr)

    // 3. Вводим инвайт код
    val joinRes = post("/partners/join") {
        header("Authorization", "Bearer ${cashierAuth.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(JoinTradingPointRequest(inviteCode))
    }

    if (joinRes.status != HttpStatusCode.OK) {
        throw IllegalStateException("Failed to join company: ${joinRes.bodyAsText()}")
    }

    // 4. Важно: После смены роли (стали кассиром) лучше обновить токен или профиль.
    // В нашем случае права проверяются динамически, так что старый токен подойдет.
    return cashierAuth.accessToken
}

/**
* Хелпер: Регистрирует юзера и МГНОВЕННО делает его Супер-Админом (через БД).
* Возвращает токены уже привилегированного пользователя.
*/
suspend fun HttpClient.registerAsAdmin(
    platformRepository: PlatformRepository,
    phone: String = "+996554190030", // Номер из конфига (для красоты)
    testDescr: String = "registerAsAdmin"
): AuthResponse {
    // 1. Регистрируемся как обычно
    val auth = this.registerAndLogin(phone = phone, testDescr = testDescr, withLogs = false)

    // 2. ХАК: Лезем в базу и выдаем права (симуляция Seeding)
    platformRepository.createSystemStaff(auth.userId, UserRole.PLATFORM_SUPER_ADMIN, "0000")

    // Возвращаем те же токены (права проверяются на лету при запросе, так что токены валидны)
    return auth
}

/**
 * Хелпер: Владельцем включаем или выключаем точку
 */
suspend fun HttpClient.changeTradingPointActivity(
    platformRepository: PlatformRepository,
    pointID: String,
    enable: Boolean
) {
    // 1. Регистрируемся как обычно
    val auth = this.registerAsAdmin(platformRepository = platformRepository)
    put("/admin/points/${pointID}/status") {
        header("Authorization", "Bearer ${auth.accessToken}")
        contentType(ContentType.Application.Json)
        setBody(ChangePointStatusRequest(isActive = enable))
    }.apply {
        assertEquals(HttpStatusCode.OK, status)
    }
}




// ...

/**
 * Хелпер: Создает торговую точку через API.
 */
suspend fun HttpClient.createTradingPoint(
    ownerToken: String,
    name: String = "Test Point",
    currency: Currency,
    type: TradingPointType = TradingPointType.COFFEE_SHOP,
    address: String = "Test Street",
    latitude: Double = 42.8746,
    longitude: Double = 74.5698,
    visitTarget: Int = 10,
    awardOnMixedPayment: Boolean = false
): String {
    val request = CreateTradingPointRequest(
        name = name,
        type = type,
        address = address,
        latitude = latitude,
        longitude = longitude,
        visitsTarget = visitTarget,
        currency = currency,
        awardOnMixedPayment = awardOnMixedPayment
    )

    val response = post("/partners/points") {
        header("Authorization", "Bearer $ownerToken")
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    if (response.status != HttpStatusCode.Created) {
        throw IllegalStateException("Failed to create point. Status: ${response.status}, Body: ${response.bodyAsText()}")
    }

    return "Created"
}

suspend fun HttpClient.processTransaction(
    accessToken: String,
    pointId: String,
    cardId: String,
    amount: Double,
    description: String,
    strategy: TransactionStrategy = TransactionStrategy.CHARGE
): TransactionResult {
    println(description)
    val response = post("/terminal/process") {
        header("Authorization", "Bearer $accessToken")
        contentType(ContentType.Application.Json)
        setBody(
            ProcessTransactionRequest(
                tradingPointId = pointId,
                cardId = cardId,
                purchaseAmount = amount,
                strategy = strategy
            )
        )
    }
    assertEquals(HttpStatusCode.OK, response.status)
    return response.body()
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
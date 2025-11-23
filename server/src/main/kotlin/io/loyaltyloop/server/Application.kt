package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.loyaltyloop.server.database.DatabaseFactory
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.routes.authRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.routes.adminRoutes
import io.loyaltyloop.server.routes.clientRoutes
import io.loyaltyloop.server.routes.partnerRoutes
import io.loyaltyloop.server.routes.terminalRoutes
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.HealthResponse
import io.loyaltyloop.shared.models.UserRole
import kotlinx.coroutines.launch
import org.slf4j.event.*



fun main(args: Array<String>) {
    // EngineMain автоматически ищет application.conf и загружает его
    io.ktor.server.netty.EngineMain.main(args)
}

val startTime = System.currentTimeMillis()

@Suppress("unused")
fun Application.module() {

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Post)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.AcceptLanguage)
        allowHeader(HttpHeaders.AccessControlAllowOrigin)

        allowCredentials = true

        // Разрешаем только наш фиксированный порт
        allowHost("localhost:3000", schemes = listOf("http", "https"))
        allowHost("127.0.0.1:3000", schemes = listOf("http", "https"))
    }

    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()



    DatabaseFactory.init(environment.config)


    // Создаем экземпляр репозитория
    val userRepository = UserRepository()
    val partnerRepository = PartnerRepository()
    val tokenService = TokenService(environment.config)
    val otpService = OtpService(environment.config)

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtIssuer)
                    .build()
            )

            validate { credential ->
                if (credential.payload.getClaim("id").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Token is invalid or expired")
            }
        }
    }

    install(CallLogging) {
        level = Level.INFO // Логируем все запросы
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            "Status: $status | Method: $httpMethod | Path: ${call.request.uri} | UA: $userAgent"
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Логируем в консоль сервера
            cause.printStackTrace()

            // Отдаем клиенту JSON с причиной
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiMessage("Server Error: ${cause.localizedMessage}")
            )
        }
    }

    val superPhone = environment.config.propertyOrNull("admin.superUserPhone")?.getString()
    val defaultPin = environment.config.propertyOrNull("admin.defaultPin")?.getString()
    if (superPhone != null) {
        launch {
            // 1. Пытаемся найти
            var user = userRepository.getUserByPhone(superPhone)

            if (user == null) {
                println("🚀 SEEDING: Creating Super User $superPhone...")
                val newId = java.util.UUID.randomUUID().toString()
                val newUser = io.loyaltyloop.shared.models.UserDto(
                    id = newId,
                    phoneNumber = superPhone,
                    countryCode = "KG",
                    firstName = null,
                    qrSecret = "admin_secret_key",
                    language = "ru"
                )
                userRepository.createUser(newUser)
                user = userRepository.getUserById(newId)
            }

            // 3. Выдаем права
            if (user != null) {
                val created = userRepository.createSystemStaff(
                    userId = user.id,
                    role = io.loyaltyloop.shared.models.UserRole.PLATFORM_SUPER_ADMIN,
                    defaultPinHash = defaultPin
                )
                if (created) println("✅ SEEDING: Admin rights granted.")
            }
        }
    }

    routing {

        swaggerUI(path = "swagger", swaggerFile = "openapi.yaml")
        get("/health") {
            // 1. Делаем реальный пинг
            val isDbAlive = DatabaseFactory.ping()

            // 2. Формируем ответ
            val response = HealthResponse(
                status = if (isDbAlive) "OK" else "ERROR",
                db = if (isDbAlive) "connected" else "disconnected",
                uptime = System.currentTimeMillis() - startTime
            )

            // 3. Выбираем HTTP код
            // Если база лежит - возвращаем 500 (Service Unavailable), чтобы мониторинг забил тревогу
            val status = if (isDbAlive) HttpStatusCode.OK else HttpStatusCode.InternalServerError

            call.respond(status, response)
        }

        get("/") {
            call.respondText("LoyaltyLoop Backend is ALIVE! 🐘")
        }

        // Подключаем наши новые маршруты
        authRoutes(userRepository, tokenService, otpService)
        clientRoutes(userRepository)
        terminalRoutes(userRepository, partnerRepository)
        partnerRoutes(userRepository = userRepository, partnerRepository = partnerRepository)
        adminRoutes(userRepository, partnerRepository)
    }
}
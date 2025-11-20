package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.loyaltyloop.server.routes.clientRoutes
import io.loyaltyloop.server.routes.terminalRoutes
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.shared.models.ApiMessage
import io.loyaltyloop.shared.models.HealthResponse
import org.slf4j.event.*



fun main(args: Array<String>) {
    // EngineMain автоматически ищет application.conf и загружает его
    io.ktor.server.netty.EngineMain.main(args)
}

val startTime = System.currentTimeMillis()

@Suppress("unused")
fun Application.module() {

    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    DatabaseFactory.init(environment.config)


    // Создаем экземпляр репозитория
    val userRepository = UserRepository()
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

    routing {
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
        terminalRoutes(userRepository)
    }
}
package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.loyaltyloop.server.database.DatabaseFactory
import io.loyaltyloop.server.repository.UserRepository
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.TransactionRepository
import io.loyaltyloop.server.repository.PinResetTokenRepository
import io.loyaltyloop.server.routes.adminRoutes
import io.loyaltyloop.server.routes.authRoutes
import io.loyaltyloop.server.routes.clientRoutes
import io.loyaltyloop.server.routes.partnerRoutes
import io.loyaltyloop.server.routes.terminalRoutes
import io.loyaltyloop.server.routes.testSupportRoutes
import io.loyaltyloop.server.routes.configRoutes
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.CardRealtimeService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.server.service.ConsoleEmailService
import io.loyaltyloop.server.repository.DeviceTokenRepository
import io.loyaltyloop.server.repository.SupportChatRepository
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.shared.models.HealthResponse
import io.loyaltyloop.server.websocket.SupportChatWebSocketHandler
import kotlinx.coroutines.launch
import org.slf4j.event.*
import io.loyaltyloop.server.utils.handleError
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.loyaltyloop.server.routes.mapsRoutes
import io.loyaltyloop.server.service.sms.ConsoleSmsService
import io.loyaltyloop.server.service.sms.InternalVerificationService
import io.loyaltyloop.server.service.sms.SmsService
import io.loyaltyloop.server.service.sms.verification.PreludeVerificationService
import java.time.Duration


fun main(args: Array<String>) {
    // EngineMain автоматически ищет application.conf и загружает его
    io.ktor.server.netty.EngineMain.main(args)
}

val startTime = System.currentTimeMillis()

@Suppress("unused")
fun Application.module() {

    install(XForwardedHeaders)

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

        // Разрешаем куки/токены
        allowCredentials = true
        allowNonSimpleContentTypes = true

        anyHost()
//        // --- ЛОГИКА ПРОВЕРКИ ДОМЕНОВ ---
//
//        // Читаем список разрешенных доменов из ENV
//        // Превращаем в список и чистим от пробелов
//
//        // 2. Разрешаем домены из ENV (Production/Stage)
//        val envHostsString = System.getenv("CORS_ALLOWED_HOSTS") ?: ""
//        val allowedHosts = envHostsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
//
//        println("🛡️ CORS Configured. Allowed hosts from ENV: $allowedHosts")
//
//        // Используем allowHost c ЛЯМБДОЙ (Предикатом).
//        // В переменную 'host' Ktor передает только домен (без https://), например "loyaltyloop.up.railway.app"
//
//
//
//        // Разбиваем строку по запятой и убираем пробелы
//        val hosts = envHostsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
//
//        if (hosts.isNotEmpty()) {
//            println("🛡️ CORS: Adding allowed hosts from ENV: $hosts")
//
//            hosts.forEach { host ->
//                // САМЫЙ ВАЖНЫЙ МОМЕНТ:
//                // Мы разрешаем этот домен И для http, И для https.
//                // Это решает проблему, когда Railway внутри своей сети обращается к контейнеру по http.
//                allowHost(host, schemes = listOf("http", "https"))
//            }
//        } else {
//            println("⚠️ CORS: Variable CORS_ALLOWED_HOSTS is empty! External requests might be blocked.")
//        }
    }


    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(30)
        timeout = Duration.ofSeconds(60)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    DatabaseFactory.init(environment.config)

    // Создаем экземпляр репозитория
    val userRepository = UserRepository()
    val partnerRepository = PartnerRepository()
    val transactionRepository = TransactionRepository()
    val pinResetTokenRepository = PinResetTokenRepository()
    val supportChatRepository = SupportChatRepository()
    val deviceTokenRepository = DeviceTokenRepository()
    val tokenService = TokenService(environment.config)
    val otpService = OtpService(environment.config)
    val cardRealtimeService = CardRealtimeService()
    val transactionService = TransactionService(
        userRepository,
        transactionRepository,
        partnerRepository,
        cardRealtimeService
    )
    val smsService = ConsoleSmsService()
    val supportChatService = SupportChatService(supportChatRepository)
    val emailService = ConsoleEmailService()
    val webBaseUrl = environment.config.propertyOrNull("app.webBaseUrl")?.getString() ?: "http://localhost:3000"
    val supportChatWebSocketHandler = SupportChatWebSocketHandler(
        tokenService = tokenService,
        partnerRepository = partnerRepository,
        userRepository = userRepository,
        supportChatService = supportChatService
    )

    // Start Background Jobs
    val loyaltyEngine = io.loyaltyloop.server.service.LoyaltyEngineService()
    loyaltyEngine.start(this)

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
            handleError(call, cause)
        }
    }

    val superPhone = environment.config.propertyOrNull("admin.superUserPhone")?.getString()
    val defaultPin = environment.config.propertyOrNull("admin.defaultPin")?.getString()
    val forcePartnerPin = environment.config.propertyOrNull("admin.forcePartnerPin")?.getString()?.takeIf { it.isNotBlank() }
    val provider = environment.config.propertyOrNull("auth.provider")?.getString() ?: "internal"

    val verificationService = if (provider == "prelude") {
        // Если в конфиге prelude - используем сервис Prelude
        InternalVerificationService(
            smsService = smsService,
            otpService = otpService
        )
    } else {
        // Иначе - используем внутреннюю логику (Console SMS + DB)
        InternalVerificationService(
            smsService = smsService,
            otpService = otpService
        )
    }

    if (forcePartnerPin != null) {
        launch {
            val affected = partnerRepository.resetAllPartnerPins(forcePartnerPin)
            log.warn("Force-set PIN for $affected partners to the configured default.")
        }
    }
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
                val created = try {
                    userRepository.createSystemStaff(
                        userId = user.id,
                        role = io.loyaltyloop.shared.models.UserRole.PLATFORM_SUPER_ADMIN,
                        defaultPinHash = defaultPin
                    )
                } catch (ex: LoyaltyException) {
                    if (ex.code != AppErrorCode.ALREADY_JOINED) {
                        throw ex
                    }
                    false
                }
                if (created) println("✅ SEEDING: Admin rights granted.")
            }
        }
    }

    routing {

        swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
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
        configRoutes(
            applicationConfig = environment!!.config,
        )
        mapsRoutes(
            applicationConfig = environment!!.config,
            partnerRepository = partnerRepository,
            userRepository = userRepository
        )
        authRoutes(userRepository, partnerRepository, tokenService, verificationService)
        clientRoutes(userRepository, deviceTokenRepository)
        terminalRoutes(userRepository = userRepository, transactionService = transactionService)
        partnerRoutes(
            userRepository = userRepository,
            partnerRepository = partnerRepository,
            transactionService = transactionService,
            pinResetTokenRepository = pinResetTokenRepository,
            emailService = emailService,
            webBaseUrl = webBaseUrl,
            supportChatService = supportChatService
        )
        adminRoutes(
            applicationConfig = environment!!.config,
            userRepo = userRepository,
            partnerRepo = partnerRepository,
            supportChatService = supportChatService
        )
        val enableTestSupport =
            environment?.config?.propertyOrNull("features.enableTestSupport")?.getString()
                ?.toBoolean() ?: false
        if (enableTestSupport) {
            testSupportRoutes(
                userRepository = userRepository,
                transactionRepository = transactionRepository,
                cardRealtimeService = cardRealtimeService
            )
        }

        webSocket("/ws/cards") {
            val token = call.request.queryParameters["token"]
            if (token.isNullOrBlank()) {
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing token"))
                return@webSocket
            }
            val userId = tokenService.validateAccessToken(token)
            if (userId.isNullOrBlank()) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid token"))
                return@webSocket
            }

            cardRealtimeService.register(userId, this)
            try {
                for (frame in incoming) {
                    // client stream is one-way for now
                }
            } finally {
                cardRealtimeService.unregister(userId, this)
            }
        }

        webSocket("/ws/support/partner") {
            supportChatWebSocketHandler.handlePartner(call, this)
        }

        webSocket("/ws/support/admin") {
            supportChatWebSocketHandler.handleAdmin(call, this)
        }
    }
}

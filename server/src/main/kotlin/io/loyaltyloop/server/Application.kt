package io.loyaltyloop.server

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
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
import io.loyaltyloop.server.repository.PlatformRepository
import io.loyaltyloop.server.repository.TransactionRepository
import io.loyaltyloop.server.repository.PinResetTokenRepository
import io.loyaltyloop.server.routes.adminRoutes
import io.loyaltyloop.server.routes.adminPlatformRoutes
import io.loyaltyloop.server.routes.authRoutes
import io.loyaltyloop.server.routes.clientRoutes
import io.loyaltyloop.server.routes.partnerRoutes
import io.loyaltyloop.server.routes.terminalRoutes
import io.loyaltyloop.server.routes.configRoutes
import io.loyaltyloop.server.routes.appRoutes
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.CardRealtimeService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.server.service.email.ConsoleEmailService
import io.loyaltyloop.server.repository.DeviceTokenRepository
import io.loyaltyloop.server.repository.SupportChatRepository
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.shared.models.HealthResponse
import io.loyaltyloop.server.websocket.SupportChatWebSocketHandler
import kotlinx.coroutines.launch
import org.slf4j.event.*
import io.loyaltyloop.server.utils.handleError
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.server.utils.long
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.loyaltyloop.server.routes.mapsRoutes
import io.loyaltyloop.server.routes.publicRoutes
import io.loyaltyloop.server.service.sms.ConsoleSmsService
import io.loyaltyloop.server.repository.WaitlistRepository
import io.loyaltyloop.server.service.sms.SmsRateLimits
import java.time.Duration
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.service.EventLogger
import io.ktor.server.plugins.ratelimit.*
import io.loyaltyloop.server.utils.bool
import kotlin.time.Duration.Companion.seconds
import io.loyaltyloop.server.service.sms.PreludeSmsService
import io.loyaltyloop.server.repository.RatingRepository
import io.loyaltyloop.server.service.RatingService
import io.loyaltyloop.server.utils.string
import so.prelude.sdk.client.okhttp.PreludeOkHttpClient
import io.loyaltyloop.server.repository.AuthSessionRepository
import io.loyaltyloop.server.service.TelegramAuthService

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
        // Allow custom headers for Device Signals
        allowHeader("X-Device-Id")
        allowHeader("X-Device-Platform")
        allowHeader("X-Device-Model")
        allowHeader("X-Os-Version")
        allowHeader("X-App-Version")

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


    install(RateLimit) {
        register {
            rateLimiter(limit = 300, refillPeriod = 60.seconds)
        }
        register(RateLimitName("auth")) {
            rateLimiter(limit = 200, refillPeriod = 60.seconds)
        }
    }



    val envConfig = environment.config

    val jwtSecret = envConfig.string("jwt.secret", "")
    val jwtIssuer = envConfig.string("jwt.issuer", "")
    val jwtAudience = envConfig.string("jwt.audience", "")
    val jwtRealm = envConfig.string("jwt.realm")

    DatabaseFactory.init(envConfig)

    // Создаем экземпляр репозитория
    val userRepository = UserRepository()
    val partnerRepository = PartnerRepository()
    val platformRepository = PlatformRepository()
    val transactionRepository = TransactionRepository()
    val pinResetTokenRepository = PinResetTokenRepository()
    val supportChatRepository = SupportChatRepository()
    val deviceTokenRepository = DeviceTokenRepository()
    val ratingRepository = RatingRepository()
    val systemEventRepository = SystemEventRepository()
    val waitlistRepository = WaitlistRepository()
    val eventLogger = EventLogger(systemEventRepository)

    val tokenService = TokenService(envConfig)
    val otpService = OtpService(envConfig)
    val cardRealtimeService = CardRealtimeService()
    
    val ratingService = RatingService(ratingRepository, transactionRepository, eventLogger, envConfig)
    
    val transactionService = TransactionService(
        userRepository,
        transactionRepository,
        partnerRepository,
        cardRealtimeService,
        eventLogger,
    )

    val supportChatService = SupportChatService(supportChatRepository)
    val emailService = ConsoleEmailService()

    var webBaseUrl = envConfig.string("app.webBaseUrl",  "http://localhost:3000")

    // Fix common configuration error where protocol is missing
    if (!webBaseUrl.startsWith("http://") && !webBaseUrl.startsWith("https://")) {
        webBaseUrl = "https://$webBaseUrl"
    }

    val supportChatWebSocketHandler = SupportChatWebSocketHandler(
        tokenService = tokenService,
        partnerRepository = partnerRepository,
        userRepository = userRepository,
        supportChatService = supportChatService
    )

    val smsProvider = envConfig.string("sms.smsProvider", "internal")
    val preludeApiKey = envConfig.string("sms.prelude_conf.apiKey", "")
    val smsRateLimits = SmsRateLimits(
        maxPerMinute = envConfig.int("sms.limits.perMinute", 1),
        maxPerHour = envConfig.int("sms.limits.perHour", 5),
        maxFailedAttempts = envConfig.int("sms.limits.maxOtpAttempts", 3),
        blockDurationMs = envConfig.long("sms.limits.blockDurationMs", 3_600_000L)
    )

    val smsService = if (smsProvider == "prelude" && preludeApiKey.isNotEmpty()) {
        PreludeSmsService(preludeApiKey, PreludeOkHttpClient.builder()
            .apiToken(preludeApiKey)
            .build(), eventLogger, emailService)
    } else {
        ConsoleSmsService(otpService, eventLogger, systemEventRepository, smsRateLimits)
    }

    val authSessionRepository = AuthSessionRepository()
    val botToken = envConfig.string("telegram.botToken", "")
    val botUsername = envConfig.string("telegram.botUsername", "")
    val autoCleanupSession = envConfig.long("telegram.autoCleanupSessionInMillis", 60_000L)
    val telegramAuthService = TelegramAuthService(authSessionRepository, userRepository, botToken, botUsername)
    telegramAuthService.start(autoCleanupSession)

    // Start Background Jobs
    val loyaltyEngine = io.loyaltyloop.server.service.LoyaltyEngineService(smsService, emailService, systemEventRepository)
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

    val superPhone = envConfig.string("admin.superUserPhone", "").ifEmpty { null }
    val defaultPin = envConfig.string("admin.defaultPin", "").ifEmpty { null }


    
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
                    qrSecret = tokenService.generateQrSecret(),
                    language = "ru"
                )
                userRepository.createUser(newUser)
                user = userRepository.getUserById(newId)
            }

            // 3. Выдаем права
            if (user != null) {
                val created = try {
                    platformRepository.createSystemStaff(
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
        val enableSwagger =  envConfig.bool("features.enableSwagger", false)
        if (enableSwagger) {
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        }

        // Подключаем наши новые маршруты
        rateLimit(RateLimitName("auth")) {
            authRoutes(userRepository, partnerRepository, tokenService, smsService, eventLogger, envConfig, telegramAuthService, authSessionRepository)
        }

        rateLimit {
            configRoutes(
                applicationConfig = environment!!.config,
            )
            mapsRoutes(
                applicationConfig = environment!!.config,
                partnerRepository = partnerRepository,
                userRepository = userRepository
            )
            publicRoutes(waitlistRepository)
            clientRoutes(userRepository,
                deviceTokenRepository,
                ratingService)
            terminalRoutes(
                userRepository = userRepository,
                transactionService = transactionService,
                ratingService = ratingService
            )
            partnerRoutes(
                userRepository = userRepository,
                partnerRepository = partnerRepository,
                transactionService = transactionService,
                pinResetTokenRepository = pinResetTokenRepository,
                emailService = emailService,
                webBaseUrl = webBaseUrl,
                supportChatService = supportChatService,
                eventLogger = eventLogger,
                ratingRepository = ratingRepository
            )
        adminRoutes(
            applicationConfig = environment!!.config,
            userRepo = userRepository,
            partnerRepo = partnerRepository,
            supportChatService = supportChatService,
            systemEventRepository = systemEventRepository
        )
            adminPlatformRoutes(
                platformRepository = platformRepository,
                userRepository = userRepository,
                emailService = emailService
            )
            appRoutes(environment!!.config)
        }
        val enableTestSupport = environment?.config?.propertyOrNull("features.enableTestSupport")?.getString()?.toBoolean() ?: false
        if (enableTestSupport) {

            get("/health") {
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

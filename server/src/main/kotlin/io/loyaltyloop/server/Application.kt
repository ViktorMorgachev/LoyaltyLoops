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
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.callloging.processingTimeMillis
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
import io.loyaltyloop.server.repository.PinResetTokenRepository
import io.loyaltyloop.server.routes.adminRoutes
import io.loyaltyloop.server.routes.adminPlatformRoutes
import io.loyaltyloop.server.routes.authRoutes
import io.loyaltyloop.server.routes.clientRoutes
import io.loyaltyloop.server.routes.partnerRoutes
import io.loyaltyloop.server.routes.terminalRoutes
import io.loyaltyloop.server.routes.configRoutes
import io.loyaltyloop.server.routes.appRoutes
import io.loyaltyloop.server.service.CardRealtimeService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.server.repository.DeviceTokenRepository
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.shared.models.HealthResponse
import io.loyaltyloop.server.websocket.SupportChatWebSocketHandler
import kotlinx.coroutines.launch
import org.slf4j.event.*
import io.loyaltyloop.server.utils.handleError
import io.loyaltyloop.server.utils.long
import io.loyaltyloop.server.utils.LoyaltyException
import io.loyaltyloop.shared.models.AppErrorCode
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.loyaltyloop.server.routes.mapsRoutes
import io.loyaltyloop.server.routes.publicRoutes
import io.loyaltyloop.server.repository.WaitlistRepository
import java.time.Duration
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.service.EventLogger
import io.ktor.server.plugins.ratelimit.*
import io.loyaltyloop.server.utils.bool
import kotlin.time.Duration.Companion.seconds
import io.loyaltyloop.server.repository.RatingRepository
import io.loyaltyloop.server.service.RatingService
import io.loyaltyloop.server.utils.string
import io.loyaltyloop.server.repository.AuthSessionRepository
import io.loyaltyloop.server.service.TelegramAuthService
import io.loyaltyloop.server.repository.MapRepository
import io.loyaltyloop.server.repository.RefreshTokenRepository
import io.loyaltyloop.server.repository.SubscriptionRepository
import io.loyaltyloop.server.service.email.EmailTemplateService
import io.loyaltyloop.server.utils.nowUtc
import io.loyaltyloop.server.utils.toUtcMillis
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.di.appModule
import io.loyaltyloop.server.di.repositoriesModule
import io.loyaltyloop.server.di.serviceModule
import io.loyaltyloop.server.repository.LoyaltyCardRepository
import io.loyaltyloop.server.repository.PartnerStaffRepository
import io.loyaltyloop.server.repository.SystemStaffRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.service.AnalyticsService
import io.loyaltyloop.server.service.ExchangeRateService
import io.loyaltyloop.server.service.LoyaltyEngineService
import io.loyaltyloop.server.service.email.EmailService
import io.loyaltyloop.server.service.sms.SmsService
import io.loyaltyloop.server.service.GeoIpService
import io.loyaltyloop.shared.models.UserDto
import io.loyaltyloop.shared.models.UserRole
import okhttp3.OkHttpClient
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.ktor.ext.inject
import org.koin.logger.slf4jLogger
import java.util.concurrent.TimeUnit

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
        allowHeader("X-Timezone-Id")
        allowHeader("X-Workspace-Id")
        allowHeader("X-Forwarded-For")

        // Разрешаем куки/токены
        allowCredentials = true
        allowNonSimpleContentTypes = true

        anyHost()
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

    // 1. Install Koin
    install(Koin) {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        slf4jLogger()
        modules(appModule(environment.config, okHttpClient = httpClient), repositoriesModule, serviceModule)
    }

    val envConfig = environment.config

    val jwtSecret = envConfig.string("jwt.secret", "")
    val jwtIssuer = envConfig.string("jwt.issuer", "")
    val jwtAudience = envConfig.string("jwt.audience", "")
    val jwtRealm = envConfig.string("jwt.realm")

    DatabaseFactory.init(envConfig)

    // 2. Inject Dependencies
    val partnerRepository by inject<PartnerRepository>()
    val partnerStaffRepository by inject<PartnerStaffRepository>()
    val tradingPointRepository by inject<TradingPointRepository>()
    val mapRepository by inject<MapRepository>()
    val systemEventRepository by inject<SystemEventRepository>()

    val exchangeRateService by inject<ExchangeRateService>()
    val loyaltyCardRepository by inject<LoyaltyCardRepository>()
    val userRepository by inject<UserRepository>()
    val platformRepository by inject<PlatformRepository>()
    val systemStaffRepository by inject<SystemStaffRepository>()
    val subscriptionRepository by inject<SubscriptionRepository>()
    val pinResetTokenRepository by inject<PinResetTokenRepository>()
    val deviceTokenRepository by inject<DeviceTokenRepository>()
    val ratingRepository by inject<RatingRepository>()
    val waitlistRepository by inject<WaitlistRepository>()
    val eventLogger by inject<EventLogger>()
    val tokenService by inject<TokenService>()
    val cardRealtimeService by inject<CardRealtimeService>()
    val ratingService by inject<RatingService>()
    val accessControlService by inject<AccessControlService>()
    val supportChatService by inject<SupportChatService>()
    val emailService by inject<EmailService>()
    val supportChatWebSocketHandler by inject<SupportChatWebSocketHandler>()
    val smsService by inject<SmsService>()
    val authSessionRepository by inject<AuthSessionRepository>()
    val telegramAuthService by inject<TelegramAuthService>()
    val refreshTokenRepository by inject<RefreshTokenRepository>()
    val emailTemplatesService by inject<EmailTemplateService>()
    val transactionService by inject<TransactionService>()
    val analyticsService by inject<AnalyticsService>()
    val loyaltyEngine by inject<LoyaltyEngineService>()

    var webBaseUrl = envConfig.string("app.webBaseUrl", "http://localhost:3000")

    // Fix common configuration error where protocol is missing
    if (!webBaseUrl.startsWith("http://") && !webBaseUrl.startsWith("https://")) {
        webBaseUrl = "https://$webBaseUrl"
    }

    val autoCleanupSession = envConfig.long("telegram.autoCleanupSessionInMillis", 60_000L)

    // Start services
    telegramAuthService.start(autoCleanupSession)
    exchangeRateService.start()
    loyaltyEngine.start()


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
                if (!credential.payload.getClaim("id").asString().isNullOrBlank()) {
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
        level = Level.INFO
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            val timeZone = call.request.headers["X-Timezone-Id"]
            val deviceId = call.request.headers["X-Device-Id"]
            val devicePlatform = call.request.headers["X-Device-Platform"]
            val deviceModel = call.request.headers["X-Device-Model"]
            val osVersion = call.request.headers["X-Os-Version"]
            val appVersion = call.request.headers["X-App-Version"]
            val dateTime = java.time.Instant.now().toString()
            val duration = call.processingTimeMillis()
            "Status: $status | Method: $httpMethod | Duration: ${duration}ms | UA: $userAgent | TZ: $timeZone | Device: [$devicePlatform | $deviceModel | $osVersion | $appVersion | ID:$deviceId] Datetime:$dateTime"
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
                val newUser = UserDto(
                    id = "will_ignored",
                    phoneNumber = superPhone,
                    countryCode = "KG",
                    firstName = null,
                    qrSecret = tokenService.generateQrSecret(),
                    language = "ru",
                    createdAt = nowUtc().toUtcMillis()
                )
                val userId = userRepository.createUser(newUser)
                user = userRepository.getUserById(userId)
            }

            // 3. Выдаем права
            if (user != null) {
                try {
                    systemStaffRepository.createSystemStaff(
                        userId = user.id,
                        role = UserRole.PLATFORM_SUPER_ADMIN,
                        defaultPinHash = defaultPin
                    )
                    println("✅ SEEDING: Admin rights granted.")
                } catch (ex: LoyaltyException) {
                    if (ex.code != AppErrorCode.ALREADY_JOINED) {
                        throw ex
                    }
                }
            }
        }
    }

    routing {
        val enableSwagger = envConfig.bool("features.enableSwagger", false)
        if (enableSwagger) {
            swaggerUI(path = "swagger", swaggerFile = "openapi/documentation.yaml")
        }

        // Подключаем наши новые маршруты
        rateLimit(RateLimitName("auth")) {
            authRoutes(
                userRepository,
                tokenService,
                smsService,
                eventLogger,
                environment!!.config,
                telegramAuthService,
                authSessionRepository,
                refreshTokenRepository,
                accessControlService,
                get<GeoIpService>()
            )
        }

        rateLimit {

            configRoutes(
                applicationConfig = environment!!.config,
            )

            mapsRoutes(
                applicationConfig = environment!!.config,
                partnerRepository = partnerRepository,
                userRepository = userRepository,
                tradingPointRepository = tradingPointRepository,
                mapRepository = mapRepository,
                accessControlService = accessControlService
            )

            publicRoutes(waitlistRepository = waitlistRepository)

            clientRoutes(
                userRepository = userRepository,
                deviceTokenRepository = deviceTokenRepository,
                ratingService = ratingService,
                loyaltyCardRepository = loyaltyCardRepository,
                accessControlService = accessControlService
            )

            terminalRoutes(
                transactionService = transactionService,
                ratingService = ratingService,
                analyticsService = analyticsService,
                accessControlService = accessControlService
            )

            partnerRoutes(
                userRepository = userRepository,
                partnerRepository = partnerRepository,
                partnerStaffRepository = partnerStaffRepository,
                tradingPointRepository = tradingPointRepository,
                transactionService = transactionService,
                pinResetTokenRepository = pinResetTokenRepository,
                emailService = emailService,
                webBaseUrl = webBaseUrl,
                supportChatService = supportChatService,
                eventLogger = eventLogger,
                ratingRepository = ratingRepository,
                deviceTokenRepository = deviceTokenRepository,
                accessControlService = accessControlService,
                analyticsService = analyticsService,
                subscriptionRepository = subscriptionRepository
            )

            adminRoutes(
                partnerRepository = partnerRepository,
                supportChatService = supportChatService,
                systemEventRepository = systemEventRepository,
                accessControlService = accessControlService,
                tradingPointRepository = tradingPointRepository,
                analyticsService = analyticsService,
                platformRepository = platformRepository
            )

            adminPlatformRoutes(
                platformRepository = platformRepository,
                systemStaffRepository = systemStaffRepository,
                accessControlService = accessControlService,
                subscriptionRepository = subscriptionRepository
            )

            appRoutes(environment!!.config)
        }
        val enableTestSupport =
            environment?.config?.propertyOrNull("features.enableTestSupport")?.getString()
                ?.toBoolean() ?: false
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
                val status =
                    if (isDbAlive) HttpStatusCode.OK else HttpStatusCode.InternalServerError

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

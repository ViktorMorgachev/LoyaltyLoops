package io.loyaltyloop.server.di

import io.ktor.server.config.ApplicationConfig
import io.loyaltyloop.server.service.AccessControlService
import io.loyaltyloop.server.service.AnalyticsService
import io.loyaltyloop.server.service.CardRealtimeService
import io.loyaltyloop.server.service.EventLogger
import io.loyaltyloop.server.service.ExchangeRateService
import io.loyaltyloop.server.service.GeoIpService
import io.loyaltyloop.server.service.LoyaltyEngineService
import io.loyaltyloop.server.service.OtpService
import io.loyaltyloop.server.service.RatingService
import io.loyaltyloop.server.service.RedisService
import io.loyaltyloop.server.service.SupportChatService
import io.loyaltyloop.server.service.TelegramAuthService
import io.loyaltyloop.server.service.TokenService
import io.loyaltyloop.server.service.TransactionService
import io.loyaltyloop.server.service.email.ConsoleEmailService
import io.loyaltyloop.server.service.email.EmailService
import io.loyaltyloop.server.service.email.EmailTemplateService
import io.loyaltyloop.server.service.email.ResendEmailService
import io.loyaltyloop.server.service.sms.ConsoleSmsService
import io.loyaltyloop.server.service.sms.PreludeSmsService
import io.loyaltyloop.server.service.sms.SmsRateLimits
import io.loyaltyloop.server.service.sms.SmsService
import io.loyaltyloop.server.utils.int
import io.loyaltyloop.server.utils.long
import io.loyaltyloop.server.utils.string
import io.loyaltyloop.server.websocket.SupportChatWebSocketHandler
import okhttp3.OkHttpClient
import org.koin.dsl.module

private const val SMS_DEFAULT_PER_HOUR = 5
private const val SMS_DEFAULT_MAX_OTP_ATTEMPTS = 3
private const val SMS_DEFAULT_BLOCK_DURATION_MS = 3_600_000L

val serviceModule = module {
    single { RedisService(get()) }
    single {
        ExchangeRateService(
            redisService = get(),
            apiKey = get<ApplicationConfig>().string("keys.exchangeRate", ""),
            okHttpClient = get<OkHttpClient>()
        )
    }
    single { GeoIpService(get()) }
    single { TokenService(get()) }
    single { OtpService(get()) }
    single { CardRealtimeService() }
    single { EventLogger(get()) }
    single { EmailTemplateService() }

    single<EmailService> {
        val config = get<ApplicationConfig>()
        val emailProvider = config.string("email.provider", "console")
        val resendApiKey = config.string("email.resend.apiKey", "")
        if (emailProvider == "resend" && resendApiKey.isNotEmpty()) {
            ResendEmailService(
                config = get(),
                eventLogger = get(),
                okHttpClient = get<OkHttpClient>()
            )
        } else ConsoleEmailService()
    }


    // SMS Service (Conditional binding logic is complex, usually handled in module or factory)
    // We can inject the dependencies to build it
    single<SmsService> {
        val config = get<ApplicationConfig>()
        val smsProvider = config.string("sms.smsProvider", "internal")

        if (smsProvider == "prelude") {
            PreludeSmsService(
                config = get<ApplicationConfig>(),
                eventLogger = get(),
                emailService = get()
            )
        } else {
            val smsRateLimits = SmsRateLimits(
                maxPerMinute = config.int("sms.limits.perMinute", 1),
                maxPerHour = config.int("sms.limits.perHour", SMS_DEFAULT_PER_HOUR),
                maxFailedAttempts = config.int("sms.limits.maxOtpAttempts", SMS_DEFAULT_MAX_OTP_ATTEMPTS),
                blockDurationMs = config.long("sms.limits.blockDurationMs", SMS_DEFAULT_BLOCK_DURATION_MS)
            )
            ConsoleSmsService(
                otpService = get(),
                eventLogger = get(),
                systemEventRepository = get(),
                limits = smsRateLimits
            )
        }
    }

    // Complex Services
    single {
        RatingService(
            get(), get(), get(), get(), get()
        )
    }

    single { AccessControlService(get(), get()) }
    single { SupportChatService(get(), get()) }
    single { AnalyticsService(get(), get(), get(), get(), get()) }

    single {
        TelegramAuthService(
            authSessionRepository = get(),
            userRepository = get(),
            botToken = get<ApplicationConfig>().string("telegram.botToken", ""),
            botUsername = get<ApplicationConfig>().string("telegram.botUsername", ""),
            webBaseUrl = get<ApplicationConfig>().string("app.webBaseUrl", "http://localhost:3000"),
            webhookUrl = get<ApplicationConfig>().string("telegram.webhookUrl", ""),
            webhookSecret = get<ApplicationConfig>().string("telegram.webhookSecret", "")
        )
    }

    single {
        LoyaltyEngineService(
            get(), get(), get(), get()
        )
    }

    single {
        TransactionService(
            get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }

    // WebSocket Handler
    single {
        SupportChatWebSocketHandler(
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
}

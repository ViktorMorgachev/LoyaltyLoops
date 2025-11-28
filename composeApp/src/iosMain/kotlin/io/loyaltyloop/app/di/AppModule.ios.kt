package io.loyaltyloop.app.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.darwin.Darwin
import io.loyaltyloop.app.data.NetworkClient
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.services.CardRealtimeService
import io.loyaltyloop.app.services.DefaultCardRealtimeService
import io.loyaltyloop.app.services.NoopPushService
import io.loyaltyloop.app.services.PushService
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule = module {
    // 1. Settings
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }

    // 2. HttpClient
    single {
        NetworkClient.create(
            engine = Darwin.create(),
            tokenStorage = get<TokenStorage>(),
            sessionManager = get<SessionManager>()
        )
    }

    single<PushService> { NoopPushService() }
    single<CardRealtimeService> { DefaultCardRealtimeService(get()) }
}
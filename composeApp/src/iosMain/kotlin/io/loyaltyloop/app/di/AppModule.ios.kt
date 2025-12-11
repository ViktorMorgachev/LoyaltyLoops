package io.loyaltyloop.app.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.darwin.Darwin
import io.loyaltyloop.app.data.ConfigStore
import io.loyaltyloop.app.data.NetworkClient
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.platform.AppRestarter
import io.loyaltyloop.app.platform.IosAppRestarter
import io.loyaltyloop.app.platform.MapInitializer
import io.loyaltyloop.app.platform.UrlOpener
import io.loyaltyloop.app.services.CardRealtimeService
import io.loyaltyloop.app.services.DefaultCardRealtimeService
import io.loyaltyloop.app.services.NoopPushService
import io.loyaltyloop.app.services.PushService
import io.loyaltyloop.app.utils.DeviceInfoProvider
import io.loyaltyloop.app.utils.IosDeviceInfoProvider
import io.loyaltyloop.app.utils.IosPlatformManager
import io.loyaltyloop.app.utils.LocationService
import io.loyaltyloop.app.utils.PlatformManager
import org.koin.dsl.module
import platform.Foundation.NSUserDefaults

actual val platformModule = module {
    // 1. Settings
    single<Settings> {
        NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
    }

    single<DeviceInfoProvider> { IosDeviceInfoProvider() }

    // 2. HttpClient
    single {
        NetworkClient.create(
            engine = Darwin.create(),
            tokenStorage = get<TokenStorage>(),
            sessionManager = get<SessionManager>(),
            deviceInfo = get()
        )
    }

    single<PlatformManager> { IosPlatformManager() }
    single<PushService> { NoopPushService() }
    single<CardRealtimeService> { DefaultCardRealtimeService(get()) }
    single<AppRestarter> { IosAppRestarter() }
    single { LocationService() }
    single { MapInitializer() }
    single { UrlOpener() }
}
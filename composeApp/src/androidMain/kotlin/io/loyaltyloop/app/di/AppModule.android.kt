package io.loyaltyloop.app.di

import android.content.Context.MODE_PRIVATE
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.okhttp.OkHttp
import io.loyaltyloop.app.data.ConfigStore
import io.loyaltyloop.app.data.NetworkClient
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.platform.AndroidAppRestarter
import io.loyaltyloop.app.platform.AppRestarter
import io.loyaltyloop.app.platform.MapInitializer
import io.loyaltyloop.app.services.AndroidPushService
import io.loyaltyloop.app.services.CardRealtimeService
import io.loyaltyloop.app.services.DefaultCardRealtimeService
import io.loyaltyloop.app.services.PushService
import io.loyaltyloop.app.utils.AndroidPlatformManager
import io.loyaltyloop.app.utils.LocationService
import io.loyaltyloop.app.utils.PlatformManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    // 1. Settings
    single<com.russhwolf.settings.Settings> {
        SharedPreferencesSettings(
            androidContext().getSharedPreferences("loyalty_prefs", MODE_PRIVATE),
            commit = true
        )
    }

    // 2. HttpClient (используем общую фабрику)
    single {
        NetworkClient.create(
            engine = OkHttp.create(),
            tokenStorage = get<TokenStorage>(),
            sessionManager = get()
        )
    }

    single<PlatformManager> { AndroidPlatformManager(androidContext()) }

    single<PushService> { AndroidPushService(androidContext(), get(), get()) }
    single<CardRealtimeService> { DefaultCardRealtimeService(get()) }
    single<AppRestarter> { AndroidAppRestarter(androidContext()) }
    single { LocationService(androidContext()) }
    single { MapInitializer(androidContext()) }
}
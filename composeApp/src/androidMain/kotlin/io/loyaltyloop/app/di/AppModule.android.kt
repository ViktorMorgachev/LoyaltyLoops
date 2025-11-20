package io.loyaltyloop.app.di

import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.okhttp.OkHttp
import io.loyaltyloop.app.data.NetworkClient // <-- Наша фабрика
import io.loyaltyloop.app.data.TokenStorage
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    // 1. Settings
    single<com.russhwolf.settings.Settings> {
        SharedPreferencesSettings(androidContext().getSharedPreferences("loyalty_prefs", 0))
    }

    // 2. HttpClient (используем общую фабрику)
    single {
        NetworkClient.create(
            engine = OkHttp.create(),
            tokenStorage = get<TokenStorage>() // Koin сам найдет TokenStorage
        )
    }
}
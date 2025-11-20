package io.loyaltyloop.app.di

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.darwin.Darwin
import io.loyaltyloop.app.data.NetworkClient
import io.loyaltyloop.app.data.TokenStorage
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
            tokenStorage = get<TokenStorage>()
        )
    }
}
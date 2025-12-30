package io.loyaltyloop.server.di

import io.ktor.server.config.ApplicationConfig
import okhttp3.OkHttpClient
import org.koin.dsl.module

fun appModule(config: ApplicationConfig, okHttpClient: OkHttpClient) = module {
    single { config }
    single { okHttpClient }
}

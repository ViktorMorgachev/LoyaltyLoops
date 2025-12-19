package io.loyaltyloop.server.di

import io.ktor.server.config.ApplicationConfig
import org.koin.dsl.module

fun appModule(config: ApplicationConfig) = module {
    single { config }
}

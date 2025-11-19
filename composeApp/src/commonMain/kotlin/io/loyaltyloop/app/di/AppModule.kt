package io.loyaltyloop.app.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import io.loyaltyloop.app.config.SERVER_URL
import io.loyaltyloop.app.features.auth.LoginScreenModel
import io.loyaltyloop.app.repository.AuthRepository
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val appModule = module {

    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(Logging) {
                logger = Logger.SIMPLE
                level = LogLevel.ALL
            }


            defaultRequest {
                url(SERVER_URL)
            }
        }
    }

    single { AuthRepository(get()) }
    factory { LoginScreenModel(get()) }

}
package io.loyaltyloop.app.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import io.loyaltyloop.app.config.SERVER_URL
import io.loyaltyloop.app.repository.AuthRepository
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val appModule = module {

    // 1. Создаем HTTP Клиент (Singleton)
    single {
        HttpClient {
            // Настраиваем JSON
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            // Устанавливаем базовый URL, чтобы не писать его везде
            defaultRequest {
                url(SERVER_URL)
            }
        }
    }

    single { AuthRepository(get()) }

    // Здесь потом будут наши ViewModel и Repository
}
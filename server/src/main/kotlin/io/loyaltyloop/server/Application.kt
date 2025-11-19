package io.loyaltyloop.server

import io.loyaltyloop.server.database.DatabaseFactory
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.routes.authRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    // EngineMain автоматически ищет application.conf и загружает его
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {


    DatabaseFactory.init(environment.config)

    // Создаем экземпляр репозитория
    val userRepository = UserRepository() // <--- Создали

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }

    routing {
        get("/") {
            call.respondText("LoyaltyLoop Backend is ALIVE! 🐘")
        }

        // Подключаем наши новые маршруты
        authRoutes(userRepository)
    }
}
package io.loyaltyloop.server

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*

/**
 * Общая конфигурация для всех тестов (База H2 + Настройки JWT)
 */
fun ApplicationTestBuilder.configureTestEnv() {
    environment {
        config = MapApplicationConfig(
            // --- DATABASE (H2 In-Memory) ---
            "storage.driverClassName" to "org.h2.Driver",
            "storage.jdbcUrl" to "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            "storage.user" to "root",
            "storage.password" to "",

            // --- JWT (Test Keys) ---
            "jwt.secret" to "test_secret_key_12345",
            "jwt.issuer" to "http://test-server/",
            "jwt.audience" to "http://test-server/hello",
            "jwt.realm" to "Test Realm",
            "jwt.accessLifetime" to "3600000",  // 1 час
            "jwt.refreshLifetime" to "86400000" // 1 день
        )
    }

    // Автоматически запускаем модуль приложения
    application {
        module()
    }
}

/**
 * Создает готовый HTTP-клиент с поддержкой JSON
 */
fun ApplicationTestBuilder.createJsonClient(): HttpClient {
    return createClient {
        install(ContentNegotiation) {
            json()
        }
    }
}

/**
 * Генерирует валидный случайный номер KG (+996 555 XX XX XX)
 */
fun generateValidPhone(): String {
    // Берем последние 6 цифр от текущего времени, чтобы обеспечить уникальность
    val uniquePart = System.currentTimeMillis().toString().takeLast(6)
    return "+996555$uniquePart"
}
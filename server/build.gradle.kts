plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "io.loyaltyloop"
version = "2.0.3"

application {
    mainClass.set("io.loyaltyloop.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))

    // Подключаем бандлы (Ktor Server + Database)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.exposed)

    // Миграции БД
    implementation(libs.flyway.core)

    // Дополнительные зависимости
    implementation(libs.okhttp)
    implementation(libs.prelude.sdk)
    implementation(libs.jedis)
    
    // Koin
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.h2.database)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}

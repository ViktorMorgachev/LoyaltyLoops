plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "io.loyaltyloop"
version = "1.0.0"

application {
    // Указываем точку входа (создадим этот файл позже)
    mainClass.set("io.loyaltyloop.server.ApplicationKt")
}

dependencies {
    // Подключаем общий код
    implementation(project(":shared"))

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.logback)

    // Database (пока просто добавим библиотеки, настроим позже)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql.driver)
    implementation(libs.hikaricp)
}
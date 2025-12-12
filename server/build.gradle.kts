plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
    application
}

group = "io.loyaltyloop"
version = "1.0.0"

application {
    mainClass.set("io.loyaltyloop.server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))

    // Подключаем бандлы (Ktor Server + Database)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.exposed)

    // Дополнительные зависимости
    implementation(libs.okhttp)
    implementation(libs.prelude.sdk)

    testImplementation(kotlin("test"))
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.h2.database)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.ktor.serialization.kotlinx.json)
}
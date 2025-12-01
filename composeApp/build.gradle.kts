@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinCocoapods) // Плагин CocoaPods
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }

    // 1. Просто объявляем цели (Targets)
    // Убрали блок .binaries.framework, так как он настраивается ниже в cocoapods
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // 2. Настройка через CocoaPods
    cocoapods {
        summary = "LoyaltyLoop Shared Module"
        homepage = "https://github.com/LoyaltyLoop"
        version = "1.0" // Версия обязательна!

        ios.deploymentTarget = "14.0"

        framework {
            baseName = "LoyaltyLoop"
            isStatic = true
            export(compose.components.resources)
        }

        // Подключение Yandex Maps
        pod("YandexMapsMobile") {
            version = "4.5.1-full"
            extraOpts += listOf("-compiler-option", "-fmodules")
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.androidx.core.ktx)
            implementation(libs.kotlinx.coroutines.android)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.kotlinx.coroutines.play.services)
            implementation(libs.process.phoenix)

            implementation(compose.uiTooling)

            // Зависимость Яндекса для Android (нативная)
            implementation(libs.yandex.maps.mobile)

            implementation(compose.components.resources)
        }

        commonMain.dependencies {
            implementation(project(":shared"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)

            implementation(libs.kermit)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.screenmodel)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.koin)
            implementation(libs.voyager.transitions)
            implementation(libs.koin.compose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.websocket)

            implementation(libs.ktor.client.auth)
            implementation(libs.multiplatform.settings)
            implementation(libs.qrose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kamel.image)

            // Пермишены (Moko)
            implementation(libs.moko.permissions.compose)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "io.loyaltyloop.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.loyaltyloop.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // KMP ресурсы подхватят языки сами, но это полезно для Android манифеста
        resConfigs("en", "ru", "be", "kk", "ky", "uz")
    }
}
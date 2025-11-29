@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "LoyaltyLoop"
            isStatic = true
        }
    }

    sourceSets {
        // --- ЗДЕСЬ МЫ УБРАЛИ getByName("main") ---
        // KMP сам найдет ресурсы, не нужно их прописывать вручную здесь.

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

            // Движок превью (важно для отображения @Preview)
            implementation(compose.uiTooling)

            // Ресурсы (важно для запуска на Android)
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
            // Основная библиотека ресурсов
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            // Аннотации для превью
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

            implementation(libs.ktor.client.auth) // Для рефреша токенов
            implementation(libs.multiplatform.settings)
            implementation(libs.qrose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kamel.image)

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
    }

    // Если вдруг понадобятся специфичные Android настройки ресурсов,
    // они пишутся здесь, в блоке android { sourceSets { ... } },
    // но сейчас они нам НЕ НУЖНЫ.
}
@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // 1. Android Target
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }

    // 2. JVM Target (ОБЯЗАТЕЛЬНО для Сервера)
    jvm()

    // 3. iOS Targets
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

android {
    namespace = "io.loyaltyloop.shared"
    compileSdk = 34

    buildFeatures.buildConfig = true

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "MAP_API_KEY", "\"913bd734-3e88-42fd-ae0d-b5f16c05110c\"")
        buildConfigField("String", "WEB_BASE_URL", "\"http://10.0.2.2:3000\"")
        buildConfigField("String", "APP_VERSION", "\"1.0.0\"")
    }
}
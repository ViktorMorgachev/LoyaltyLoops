@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildConfig)
}

buildConfig {
    packageName("io.loyaltyloop.shared")
    buildConfigField("String", "APP_VERSION", "\"1.0.0\"")
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

    defaultConfig {
        minSdk = 24
    }
}
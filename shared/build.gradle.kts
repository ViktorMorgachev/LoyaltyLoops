@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.buildConfig)
}

// 1. Логика окружения (Копия из composeApp для синхронизации)
val activeEnv = project.findProperty("env") as? String
    ?: listOf("stage", "prod").firstOrNull {
        gradle.startParameter.taskNames.any { task -> task.contains(it, true) }
    } ?: "dev"

// 2. Extension для чистоты
fun com.github.gmazzo.buildconfig.BuildConfigExtension.stringField(name: String, value: String) =
    buildConfigField("String", name, "\"$value\"")

buildConfig {
    packageName("io.loyaltyloop.shared")

    stringField("APP_VERSION", "1.1.0")
    buildConfigField("int", "VERSION_CODE", "110")
    buildConfigField("boolean", "IS_PROD", "${activeEnv == "prod"}")
    stringField("ENV_NAME", activeEnv)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
    }

    jvm()

    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.koin.core)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "io.loyaltyloop.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

}
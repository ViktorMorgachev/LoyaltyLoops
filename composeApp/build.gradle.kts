@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.buildConfig)
}

// Читаем свойство из командной строки (например: ./gradlew assemble -Penv=prod)
// По умолчанию 'dev'
val activeEnv = project.findProperty("env") as? String ?: "dev"
val isServerBuild = project.hasProperty("serverBuild")

buildConfig {
    packageName("io.loyaltyloop.app.config")
    className("AppConfig")

    // Логика выбора URL
    val serverUrl = when(activeEnv) {
        "prod" -> "https://api.loyaltyloop.kg"
        "stage" -> "https://api-test.loyaltyloop.kg"
        // Локалхост для Android эмулятора.
        // Для iOS эмулятора это должен быть localhost или 127.0.0.1, но 10.0.2.2 тоже иногда мапится, но надежнее localhost.
        // Однако, 10.0.2.2 - стандарт Android.
        // Для универсальности в локальной разработке часто используют IP машины в сети, но пока оставим так.
        else -> "http://10.0.2.2:8080"
    }

    val webUrl = when(activeEnv) {
        "prod" -> "https://admin.loyaltyloop.kg"
        "stage" -> "https://admin-stage.loyaltyloop.kg"
        else -> "http://10.0.2.2:3000"
    }

    buildConfigField("String", "WEB_URL", "\"$webUrl\"")
    buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
    buildConfigField("String", "MAP_API_KEY", "\"ffb31301-c998-483f-95a7-729f5f29ac1d\"")
    
    // Добавляем инфо о текущем окружении
    buildConfigField("String", "ENV_NAME", "\"$activeEnv\"")
}

kotlin {
    if (!isServerBuild){
        androidTarget {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
            }
        }

        iosX64()
        iosArm64()
        iosSimulatorArm64()

        cocoapods {
            summary = "LoyaltyLoop Shared Module"
            homepage = "https://github.com/LoyaltyLoop"
            version = "1.0"

            ios.deploymentTarget = "14.0"

            framework {
                baseName = "LoyaltyLoop"
                isStatic = true
                export(compose.components.resources)
            }

            pod("YandexMapsMobile") {
                version = "4.5.1-full"
                extraOpts += listOf("-compiler-option", "-fmodules")
            }
        }
    }



    sourceSets {
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

            implementation(libs.moko.permissions.compose)
        }
        if (!isServerBuild){
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

                implementation(libs.yandex.maps.mobile)

                implementation(compose.components.resources)
            }



            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
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
        resConfigs("en", "ru", "be", "kk", "ky", "uz")
    }
}

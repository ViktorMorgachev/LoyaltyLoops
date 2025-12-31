@file:OptIn(ExperimentalKotlinGradlePluginApi::class)
import java.util.Properties
import java.io.FileInputStream
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
    alias(libs.plugins.kotlinCocoapods)
    alias(libs.plugins.buildConfig)
}

// 1. Определение окружения
val activeEnv = project.findProperty("env") as? String
    ?: gradle.startParameter.taskNames.any { it.contains("stage", true) }.let { if (it) "stage" else null }
    ?: "dev"

val isServerBuild = project.hasProperty("serverBuild")
val isProd = activeEnv == "prod"

val currentVersionCode = 111
val currentVersionName = "1.1.1"

// 2. Extension для красивой записи строк в BuildConfig
fun com.github.gmazzo.buildconfig.BuildConfigExtension.stringField(name: String, value: String) =
    buildConfigField("String", name, "\"$value\"")

buildConfig {
    packageName("io.loyaltyloop.app.config")
    className("AppConfig")

    buildConfigField("int", "VERSION_CODE", "$currentVersionCode")
    stringField("VERSION_NAME", currentVersionName)
    buildConfigField("boolean", "IS_PROD", "$isProd")
    stringField("ENV_NAME", activeEnv)
    stringField("MAP_API_KEY", "913bd734-3e88-42fd-ae0d-b5f16c05110c")

    val (serverUrl, webUrl) = when (activeEnv) {
        "prod" -> "https://server-loyalityloop-prod.up.railway.app" to "https://loyalityloop.up.railway.app"
        "stage" -> "https://server-loyalityloop-stage.up.railway.app" to "https://loyalityloop-beta.up.railway.app"
        else ->  "https://server-loyalityloop-stage.up.railway.app" to "https://loyalityloop-beta.up.railway.app"
    }
    stringField("SERVER_URL", serverUrl)
    stringField("WEB_URL", webUrl)
}

kotlin {
    if (!isServerBuild) {
        androidTarget {
            compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
        }
        iosX64(); iosArm64(); iosSimulatorArm64()

        cocoapods {
            summary = "LoyaltyLoop Shared Module"
            homepage = "https://github.com/LoyaltyLoop"
            version = "1.1.1"
            ios.deploymentTarget = "14.0"
            framework {
                baseName = "LoyaltyLoop"
                isStatic = true
                export(compose.components.resources)
            }

            pod("YandexMapsMobile") {
                version = "4.5.1-lite"
                extraOpts += listOf("-compiler-option", "-fmodules")
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))

            // Compose (из плагина)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.uiToolingPreview)

            // Используем Bundles (сокращает 15 строк кода)
            implementation(libs.bundles.voyager)
            implementation(libs.bundles.ktor.client)

            implementation(libs.koin.compose)
            implementation(libs.kermit)
            implementation(libs.multiplatform.settings)
            implementation(libs.qrose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kamel.image)
            implementation(libs.moko.permissions.compose)
        }

        if (!isServerBuild) {
            androidMain.dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.androidx.activity.compose)
                implementation(libs.koin.android)
                implementation(libs.androidx.core.ktx)
                implementation(libs.process.phoenix)
                implementation(libs.yandex.maps.mobile)
                implementation(libs.android.material)

                // Firebase & Updates
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.messaging)
                implementation(libs.firebase.crashlytics)
                implementation(libs.play.app.update.ktx)

                // CameraX Bundle
                implementation(libs.bundles.camerax)
            }

            iosMain.dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
    }
}

val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
android {
    namespace = "io.loyaltyloop.app"
    compileSdk = 36

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"] as String?
            keyPassword = keystoreProperties["keyPassword"] as String?
            storeFile = file(keystoreProperties["storeFile"] as String? ?: "loyalty_release.jks")
            storePassword = keystoreProperties["storePassword"] as String?

            // ВАЖНО: Включаем обе схемы для совместимости
            enableV1Signing = true // Jar Signature (для старых Android)
            enableV2Signing = true // Full APK Signature (для новых Android и Play Protect)
        }
    }

    defaultConfig {
        applicationId = "io.loyaltyloop.app"
        minSdk = 26
        targetSdk = 35
        versionCode = currentVersionCode
        versionName = currentVersionName
        resourceConfigurations += setOf("en", "ru", "be", "kk", "ky", "uz")
    }

    flavorDimensions += "brand"

    productFlavors {
        create("loyaltyloop") { dimension = "brand" }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            // Видимое имя в проде
            resValue("string", "app_name", "LoyaltyLoop")
        }
        create("stage") {
            initWith(getByName("release"))
            applicationIdSuffix = ".stage"
            matchingFallbacks.add("release")
            signingConfig = signingConfigs.getByName("debug")
            // Видимое имя на стейдже
            resValue("string", "app_name", "LoyaltyLoop Stage")
        }
    }

    applicationVariants.all {
        val variant = this
        if (buildType.name == "release") {
            outputs.all {
                // Меняем имя файла на LoyaltyLoop.apk
                (this as? com.android.build.gradle.internal.api.BaseVariantOutputImpl)?.outputFileName = "LoyaltyLoop.apk"
            }

            // Копируем APK в нужные папки после сборки
            assembleProvider.configure {
                doLast {
                    variant.outputs.forEach { output ->
                        val apkFile = output.outputFile
                        if (apkFile != null && apkFile.exists() && apkFile.name.endsWith(".apk")) {
                            val rootDir = project.rootDir

                            val destDirWeb = rootDir.resolve("web-admin/public")
                            copy {
                                from(apkFile)
                                into(destDirWeb)
                            }

                            println("✅ Release APK copied to:")
                            println("   - ${destDirWeb.absolutePath}/${apkFile.name}")
                        }
                    }
                }
            }
        }
    }
}
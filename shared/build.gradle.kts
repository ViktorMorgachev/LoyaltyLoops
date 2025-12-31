@file:OptIn(ExperimentalKotlinGradlePluginApi::class)
import java.util.Properties
import java.io.FileInputStream
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

// 2. Читаем версии из local.properties с безопасными дефолтами
val keystorePropertiesFile = rootProject.file("local.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun getStringProp(key: String, default: String): String =
    (keystoreProperties.getProperty(key) ?: default).toString()

fun getIntProp(key: String, default: Int): Int =
    getStringProp(key, default.toString()).toInt()

// 3. Extension для чистоты
fun com.github.gmazzo.buildconfig.BuildConfigExtension.stringField(name: String, value: String) =
    buildConfigField("String", name, "\"$value\"")

buildConfig {
    packageName("io.loyaltyloop.shared")

    val versionCode = getIntProp("currentVersionCode", 111)
    val versionName = getStringProp("currentVersionName", "1.1.1")

    stringField("APP_VERSION", versionName)
    buildConfigField("int", "VERSION_CODE", "$versionCode")
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
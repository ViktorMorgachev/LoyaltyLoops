import io.gitlab.arturbosch.detekt.extensions.DetektExtension
plugins {
    // Просто объявляем плагины, но не применяем (apply false)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.sqlDelight) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.kotlinCocoapods) apply false
}

allprojects {
    // Применяем плагин
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // 2. ИСПОЛЬЗУЕМ ЯВНУЮ КОНФИГУРАЦИЮ
    extensions.configure<DetektExtension> {
        toolVersion = "1.23.6"
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
        // Легаси-замечания зафиксированы в baseline (генерация: ./gradlew detektBaseline).
        // Новый код обязан быть чистым — baseline не обновлять без ревью.
        baseline = file("$rootDir/config/detekt/baseline-${project.name}.xml")
    }
}
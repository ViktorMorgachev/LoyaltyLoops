package io.loyaltyloop.app.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.serialization.kotlinx.json.json
import io.loyaltyloop.app.config.SERVER_URL
import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.features.auth.LoginScreenModel
import io.loyaltyloop.app.features.onboarding.OnboardingScreenModel
import io.loyaltyloop.app.features.profile.ProfileScreenModel
import io.loyaltyloop.app.features.role.RoleSelectionScreenModel
import io.loyaltyloop.app.features.splash.SplashScreenModel
import io.loyaltyloop.app.features.wallet.WalletScreenModel
import io.loyaltyloop.app.repository.AuthRepository
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val appModule = module {

    includes(platformModule)

    // TokenStorage зависит от Settings (которые придут из platformModule)
    single { TokenStorage(get()) }

    single { AuthRepository(get()) }
    single { SessionManager(get()) }
    factory { SplashScreenModel(get(), get(), get()) }
    factory { LoginScreenModel(get(),get(), get()) }
    factory { OnboardingScreenModel(get()) }
    factory { RoleSelectionScreenModel(get()) }
    factory { WalletScreenModel(get()) }
    factory { ProfileScreenModel(get(), get(), get()) }

}
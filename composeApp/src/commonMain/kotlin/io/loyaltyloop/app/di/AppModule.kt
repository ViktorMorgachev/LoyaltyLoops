package io.loyaltyloop.app.di

import io.loyaltyloop.app.data.SessionManager
import io.loyaltyloop.app.data.TokenStorage
import io.loyaltyloop.app.features.auth.LoginScreenModel
import io.loyaltyloop.app.features.join.JoinCompanyScreenModel
import io.loyaltyloop.app.features.onboarding.OnboardingScreenModel
import io.loyaltyloop.app.features.profile.ProfileScreenModel
import io.loyaltyloop.app.features.role.RoleSelectionScreenModel
import io.loyaltyloop.app.features.splash.SplashScreenModel
import io.loyaltyloop.app.features.terminal.TerminalScreenModel
import io.loyaltyloop.app.features.terminal.confirmation.TransactionConfirmationScreenModel
import io.loyaltyloop.app.features.terminal.result.TerminalResultScreenModel
import io.loyaltyloop.app.features.wallet.WalletScreenModel
import io.loyaltyloop.app.repository.AuthRepository
import io.loyaltyloop.app.repository.PartnerRepository
import io.loyaltyloop.app.repository.WalletRepository
import io.loyaltyloop.shared.models.ScanQrResponse
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import org.koin.core.module.Module
import org.koin.dsl.module

expect val platformModule: Module

val appModule = module {

    includes(platformModule)

    // TokenStorage зависит от Settings (которые придут из platformModule)
    single { TokenStorage(get()) }
    single { SessionManager(get()) }

    // Репозитории
    single { AuthRepository(get()) }
    single { PartnerRepository(get()) }
    single { WalletRepository(get()) }

    // ViewModels
    factory { SplashScreenModel(get(), get(), get(), get()) }
    factory { LoginScreenModel(get(), get(), get(), get()) }
    factory { OnboardingScreenModel(get(), get(), get()) }
    factory { RoleSelectionScreenModel(get()) }
    factory { WalletScreenModel(get(),get(), get()) }
    factory { ProfileScreenModel(get(), get(), get()) }
    factory { JoinCompanyScreenModel(get()) }

    factory { TerminalScreenModel(get(), get()) }
    factory { (scanData: ScanQrResponse, tradingPointId: String, strategy: TransactionStrategy) ->
        TerminalResultScreenModel(scanData, tradingPointId, strategy, get())
    }
    factory { (calc: TransactionCalculationDto, tpId: String, cardId: String, strategy: TransactionStrategy) ->
        TransactionConfirmationScreenModel(calc, tpId, cardId, strategy, get())
    }

}

package io.loyaltyloop.server.di

import io.loyaltyloop.server.repository.*
import org.koin.dsl.module

val repositoriesModule = module {
    single { AuthSessionRepository() }
    single { DeviceTokenRepository() }
    single { HistoryRepository() }
    single { LoyaltyCardRepository(get()) }
    single { MapRepository() }
    single { PartnerRepository(get()) }
    single { PartnerStaffRepository() }
    single { PinResetTokenRepository() }
    single { PlatformRepository(get()) }
    single { RatingRepository() }
    single { RefreshTokenRepository() }
    single { SubscriptionRepository() }
    single { SupportChatRepository() }
    single { SystemEventRepository() }
    single { SystemStaffRepository() }
    single { TradingPointRepository() }
    single { UserRepository() }
    single { WaitlistRepository() }
}

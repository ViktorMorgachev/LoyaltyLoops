package io.loyaltyloop.server.di

import io.loyaltyloop.server.repository.AuthSessionRepository
import io.loyaltyloop.server.repository.DeviceTokenRepository
import io.loyaltyloop.server.repository.HistoryRepository
import io.loyaltyloop.server.repository.LoyaltyCardRepository
import io.loyaltyloop.server.repository.MapRepository
import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.server.repository.PartnerStaffRepository
import io.loyaltyloop.server.repository.PinResetTokenRepository
import io.loyaltyloop.server.repository.PlatformRepository
import io.loyaltyloop.server.repository.RatingRepository
import io.loyaltyloop.server.repository.RefreshTokenRepository
import io.loyaltyloop.server.repository.SubscriptionRepository
import io.loyaltyloop.server.repository.SupportChatRepository
import io.loyaltyloop.server.repository.SystemEventRepository
import io.loyaltyloop.server.repository.SystemStaffRepository
import io.loyaltyloop.server.repository.TradingPointRepository
import io.loyaltyloop.server.repository.UserRepository
import io.loyaltyloop.server.repository.WaitlistRepository
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

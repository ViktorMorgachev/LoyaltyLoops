package io.loyaltyloop.shared.config

import io.loyaltyloop.shared.BuildConfig

actual object AppConfig {
    actual val featureFlags: FeatureFlags = FeatureFlags(
        realtimeEnabled = true,
        pushEnabled = true
    )
    actual val webBaseUrl: String = BuildConfig.WEB_BASE_URL
}


package io.loyaltyloop.shared.config

import io.loyaltyloop.shared.BuildConfig

actual object AppConfig {
    actual val featureFlags: FeatureFlags = FeatureFlags(
        realtimeEnabled = true
    )
    actual val appVersion: String = BuildConfig.APP_VERSION
}


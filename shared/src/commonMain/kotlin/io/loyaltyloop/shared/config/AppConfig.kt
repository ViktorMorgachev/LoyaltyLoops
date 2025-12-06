package io.loyaltyloop.shared.config

import io.loyaltyloop.shared.BuildConfig

data class FeatureFlags(
    val realtimeEnabled: Boolean,
)

object AppConfig {
    val featureFlags: FeatureFlags = FeatureFlags(
        realtimeEnabled = true
    )
    val appVersion: String = BuildConfig.APP_VERSION
}


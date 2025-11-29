package io.loyaltyloop.shared.config

actual object AppConfig {
    actual val featureFlags: FeatureFlags = FeatureFlags(
        realtimeEnabled = true,
        pushEnabled = true
    )
}


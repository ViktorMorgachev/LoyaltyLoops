package io.loyaltyloop.shared.config

actual object AppConfig {
    actual val featureFlags: FeatureFlags
        get() = FeatureFlags(realtimeEnabled = true, pushEnabled = true)
}
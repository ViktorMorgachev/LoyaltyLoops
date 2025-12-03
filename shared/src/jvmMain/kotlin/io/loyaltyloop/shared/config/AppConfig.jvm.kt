package io.loyaltyloop.shared.config

actual object AppConfig {
    actual val featureFlags: FeatureFlags
        get() = FeatureFlags(realtimeEnabled = true)

    actual val appVersion: String
        get() = "1.0.0"
}
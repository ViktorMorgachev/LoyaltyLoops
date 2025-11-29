package io.loyaltyloop.shared.config

actual object AppConfig {
    actual val featureFlags: FeatureFlags = FeatureFlags(
        realtimeEnabled = true,
        pushEnabled = false
    )
    actual val webBaseUrl: String = "https://app.loyaltyloop.io"
}


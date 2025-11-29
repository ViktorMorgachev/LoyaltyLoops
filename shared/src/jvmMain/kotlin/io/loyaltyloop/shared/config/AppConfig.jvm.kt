package io.loyaltyloop.shared.config

actual object AppConfig {
    actual val featureFlags: FeatureFlags
        get() = FeatureFlags(realtimeEnabled = true, pushEnabled = true)
    actual val webBaseUrl: String
        get() =""// BuildConfig.WEB_BASE_URL
}
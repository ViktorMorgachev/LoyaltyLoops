package io.loyaltyloop.shared.config

data class FeatureFlags(
    val realtimeEnabled: Boolean,
)

expect object AppConfig {
    val featureFlags: FeatureFlags
    val appVersion: String
}


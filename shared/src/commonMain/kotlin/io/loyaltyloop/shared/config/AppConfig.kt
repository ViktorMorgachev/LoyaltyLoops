package io.loyaltyloop.shared.config

data class FeatureFlags(
    val realtimeEnabled: Boolean,
    val pushEnabled: Boolean
)

expect object AppConfig {
    val featureFlags: FeatureFlags
}


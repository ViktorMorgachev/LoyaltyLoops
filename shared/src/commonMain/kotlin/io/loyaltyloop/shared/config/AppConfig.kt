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
    val isProd = BuildConfig.IS_PROD
    const val VERSION_CODE: Int = BuildConfig.VERSION_CODE
}


package io.loyaltyloop.shared.config

import platform.Foundation.NSBundle

actual object AppConfig {
    actual val featureFlags: FeatureFlags = FeatureFlags(
        realtimeEnabled = true
    )
    actual val appVersion: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: "1.0.0"
}


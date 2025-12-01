package io.loyaltyloop.app.config

import io.loyaltyloop.shared.BuildConfig

actual val SERVER_URL: String
    get() = "http://10.0.2.2:8080"
actual val MAP_API_KEY: String
    get() = BuildConfig.MAP_API_KEY
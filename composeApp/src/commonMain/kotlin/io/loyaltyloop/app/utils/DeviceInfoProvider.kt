package io.loyaltyloop.app.utils

interface DeviceInfoProvider {
    val deviceId: String
    val platform: String
    val model: String
    val osVersion: String
    val appVersion: String
}


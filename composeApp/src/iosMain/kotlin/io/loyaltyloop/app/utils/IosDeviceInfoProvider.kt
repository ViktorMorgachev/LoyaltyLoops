package io.loyaltyloop.app.utils

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

class IosDeviceInfoProvider : DeviceInfoProvider {
    override val deviceId: String
        get() = UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown_ios_id"

    override val platform: String = "ios"

    override val model: String
        get() = UIDevice.currentDevice.model

    override val osVersion: String
        get() = UIDevice.currentDevice.systemVersion

    override val appVersion: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "unknown"
}


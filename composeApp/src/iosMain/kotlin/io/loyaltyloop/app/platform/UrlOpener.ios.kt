package io.loyaltyloop.app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class UrlOpener {
    actual fun openUrl(url: String) {
        val nsUrl = NSURL(string = url)
        if (UIApplication.sharedApplication.canOpenURL(nsUrl)) {
            UIApplication.sharedApplication.openURL(nsUrl)
        }
    }
}



package io.loyaltyloop.app.utils

import platform.Foundation.NSUserDefaults

class IosPlatformManager : PlatformManager {
    override fun applyLanguage(languageCode: String) {
        // На iOS обычно сохраняют в NSUserDefaults "AppleLanguages"
         NSUserDefaults.standardUserDefaults.setObject(listOf(languageCode), "AppleLanguages")
         NSUserDefaults.standardUserDefaults.synchronize()
    }

    override fun reloadUI() {
        // На iOS нельзя программно перезапустить приложение.
        // Обычно здесь просто перерисовывают Root View Controller.
        // Либо шлют нотификацию в NSNotificationCenter.
    }

}
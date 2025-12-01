package io.loyaltyloop.app.utils

import android.content.Context
import android.content.Intent
import io.loyaltyloop.app.MainActivity // Твоя главная активити
import java.util.Locale

class AndroidPlatformManager(private val context: Context) : PlatformManager {

    override fun applyLanguage(languageCode: String) {
        LocaleManager.applyLocale(context, languageCode)
    }

    override fun reloadUI() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
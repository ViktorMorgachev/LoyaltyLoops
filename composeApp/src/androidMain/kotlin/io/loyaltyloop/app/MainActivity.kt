package io.loyaltyloop.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.navigator.currentOrThrow
import co.touchlab.kermit.Logger
import io.loyaltyloop.app.navigation.NavigatorHolder
import io.loyaltyloop.app.features.wallet.LoyaltyCardDetailsScreen
import io.loyaltyloop.app.services.LoyaltyFirebaseMessagingService
import io.loyaltyloop.app.utils.LocaleManager
import io.loyaltyloop.shared.config.AppConfig

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.d { "Notification permission granted = $granted" }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)

        setContent {
            App()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val prefs = newBase.getSharedPreferences("loyalty_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_language", "ru") ?: "ru" // "ru" - дефолт

        val context = LocaleManager.applyLocale(newBase, lang)
        super.attachBaseContext(context)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (!AppConfig.featureFlags.pushEnabled) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val cardId = intent?.getStringExtra(LoyaltyFirebaseMessagingService.EXTRA_CARD_ID) ?: return
        intent.removeExtra(LoyaltyFirebaseMessagingService.EXTRA_CARD_ID)
        Logger.d { "Notification tapped for cardId=$cardId" }
        NavigatorHolder.lastNavigator?.push(LoyaltyCardDetailsScreen(cardId))
    }
}
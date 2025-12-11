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
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.navigator.currentOrThrow
import co.touchlab.kermit.Logger
import io.loyaltyloop.app.navigation.NavigatorHolder
import io.loyaltyloop.app.features.wallet.LoyaltyCardDetailsScreen
import io.loyaltyloop.app.services.LoyaltyFirebaseMessagingService
import io.loyaltyloop.app.utils.LocaleManager
import io.loyaltyloop.shared.config.AppConfig
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

class MainActivity : ComponentActivity() {

    private val UPDATE_REQUEST_CODE = 500

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Logger.d { "Notification permission granted = $granted" }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = AppConfig.isProd
        handleNotificationIntent(intent)
        checkForFlexibleUpdate()

        setContent {
            App()
        }
    }

    private fun checkForFlexibleUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    AppUpdateType.FLEXIBLE,
                    this,
                    UPDATE_REQUEST_CODE
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                popupSnackbarForCompleteUpdate()
            }
        }
    }

    private fun popupSnackbarForCompleteUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(this)
        Snackbar.make(
            findViewById(android.R.id.content),
            getString(R.string.update_downloaded_message),
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction(getString(R.string.update_restart_btn)) { appUpdateManager.completeUpdate() }
            show()
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
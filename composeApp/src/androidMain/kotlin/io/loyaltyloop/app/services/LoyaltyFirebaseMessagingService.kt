package io.loyaltyloop.app.services

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import co.touchlab.kermit.Logger as KermitLogger
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.loyaltyloop.app.MainActivity
import io.loyaltyloop.app.R
import io.loyaltyloop.app.utils.LogType
import io.loyaltyloop.app.utils.write

class LoyaltyFirebaseMessagingService : FirebaseMessagingService() {

    private val logger = KermitLogger.withTag("FirebaseMessagingService")

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        logger.write("FCM token refreshed: $token", type = LogType.Debug)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        NotificationChannels.ensureCreated(this)

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: getString(R.string.notif_default_body)
        val cardId = message.data["cardId"]

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CARD_ID, cardId)
        }

        val requestCode = cardId?.hashCode() ?: System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CARDS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_default)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                logger.write("Skipping notification: POST_NOTIFICATIONS not granted", type = LogType.Info)
                return
            }
        }

        NotificationManagerCompat.from(this).notify(requestCode, notification)
    }

    companion object {
        const val EXTRA_CARD_ID = "cardId"
    }
}


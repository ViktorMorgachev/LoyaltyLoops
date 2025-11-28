package io.loyaltyloop.app.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CARDS_CHANNEL_ID = "cards_updates"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val existing = manager.getNotificationChannel(CARDS_CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CARDS_CHANNEL_ID,
                    "События карт лояльности",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Начисления, списания и награды"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}


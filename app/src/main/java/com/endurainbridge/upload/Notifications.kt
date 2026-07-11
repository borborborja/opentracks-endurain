package com.endurainbridge.upload

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.app.Notification
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.endurainbridge.R

object Notifications {

    const val CHANNEL_ID = "endurain_uploads"
    private const val NOTIF_ID_RESULT = 1001
    const val NOTIF_ID_WATCH = 1002

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_uploads),
            NotificationManager.IMPORTANCE_LOW,
        )
        val mgr = context.getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(channel)
    }

    fun showResult(context: Context, title: String, text: String) {
        if (!hasPermission(context)) return
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_RESULT, notif)
    }

    /** Ongoing notification shown while the watch service waits for recording to finish. */
    fun buildWatching(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_upload)
            .setContentTitle(context.getString(R.string.notif_watching))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}

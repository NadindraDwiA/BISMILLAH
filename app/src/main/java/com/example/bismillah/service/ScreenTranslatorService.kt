package com.example.bismillah.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenTranslatorService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "screen_translator"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                NotificationChannel(channelId, "Screen Translator", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Screen Translator aktif")
            .setContentText("Bubble siap — ketuk untuk menerjemahkan layar")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        try {
            startForeground(1, notif)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        try { com.example.bismillah.ui.bubble.BubbleSession.stop() } catch (_: Exception) {}
        MediaProjectionHolder.clear()
        super.onDestroy()
    }
}

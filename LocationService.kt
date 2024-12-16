package com.example.luna

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LocationService : Service() {

    companion object {
        private const val CHANNEL_ID = "LocationServiceChannel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Start the service as a foreground service
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Luna App")
            .setContentText("Tracking location for your safety")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(1, notification) // Call startForeground here
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle service logic (e.g., location updates)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cleanup resources if needed
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Return null for a non-bound service
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}

package com.henderjj.carfinder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.content.getSystemService
import org.osmdroid.config.Configuration

class CarFinderApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // osmdroid needs a user-agent string and a config load before any MapView is created.
        Configuration.getInstance().apply {
            load(this@CarFinderApp, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = packageName
        }

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService<NotificationManager>() ?: return

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CAPTURE,
                getString(R.string.notif_channel_capture_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = getString(R.string.notif_channel_capture_description) }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.notif_channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.notif_channel_service_description) }
        )
    }

    companion object {
        const val CHANNEL_CAPTURE = "parking_capture"
        const val CHANNEL_SERVICE = "parking_service"
    }
}

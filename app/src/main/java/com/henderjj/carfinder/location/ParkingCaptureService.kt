package com.henderjj.carfinder.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.henderjj.carfinder.CarFinderApp
import com.henderjj.carfinder.MainActivity
import com.henderjj.carfinder.R
import com.henderjj.carfinder.data.AppPreferences
import com.henderjj.carfinder.data.ParkingLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Short-lived foreground service that fetches a single high-accuracy location fix
 * and persists it as the current parking location. Stops itself once done.
 */
class ParkingCaptureService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private val cancellationSource = CancellationTokenSource()
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        // Entering the foreground can be refused (SecurityException /
        // ForegroundServiceStartNotAllowedException) on Android 14+ if the system
        // decides we aren't eligible to run a location-typed FGS from the background
        // — e.g. background location was revoked since the receiver's pre-flight
        // check. That must not crash the process; we just skip this capture.
        foregroundStarted = try {
            startForegroundWithNotification()
            true
        } catch (e: Exception) {
            Log.e(TAG, "System refused foreground start — skipping capture", e)
            false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundStarted) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (job?.isActive == true) return START_NOT_STICKY

        if (!hasFineLocationPermission()) {
            Log.w(TAG, "Fine location permission missing — cannot capture parking")
            stopSelfSafely()
            return START_NOT_STICKY
        }

        job = scope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(this@ParkingCaptureService)
                // Permission is verified by hasFineLocationPermission() before launching, and a
                // SecurityException from a mid-flight revocation is caught below; lint can't see
                // through the helper.
                @SuppressLint("MissingPermission")
                val location = client.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationSource.token
                ).await()
                if (location == null) {
                    Log.w(TAG, "No location returned")
                } else {
                    val parked = ParkingLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
                        capturedAtEpochMillis = System.currentTimeMillis()
                    )
                    AppPreferences.get(this@ParkingCaptureService).saveParking(parked)
                    notifyCaptured()
                    Log.i(TAG, "Saved parking @ ${parked.latitude},${parked.longitude} (±${parked.accuracyMeters}m)")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to capture parking location", t)
            } finally {
                stopSelfSafely()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, CarFinderApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notif_service_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        // Foreground service types only exist on API 29+; on API 28 ServiceCompat ignores the type.
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_SERVICE, notification, type)
    }

    private fun notifyCaptured() {
        // Content intent: open the app normally.
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Action intent: open the app AND immediately show the bay-details dialog.
        val openDetails = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_DETAILS, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val n = NotificationCompat.Builder(this, CarFinderApp.CHANNEL_CAPTURE)
            .setContentTitle(getString(R.string.notif_capture_title))
            .setContentText(getString(R.string.notif_capture_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(openApp)
            .addAction(
                android.R.drawable.ic_menu_edit,
                getString(R.string.notif_action_add_details),
                openDetails
            )
            .setAutoCancel(true)
            .build()

        // POST_NOTIFICATIONS is runtime-gated on Android 13+. If we don't have it,
        // notify() silently fails — that's fine for the prototype.
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_CAPTURED, n)
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        cancellationSource.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CarFinder.Capture"
        private const val NOTIF_SERVICE = 1001
        private const val NOTIF_CAPTURED = 1002

        fun start(context: Context) {
            val intent = Intent(context, ParkingCaptureService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * True when the app holds everything a background-started, location-typed
         * foreground service needs: fine location, plus background location on
         * Android 10+. Used by [com.henderjj.carfinder.bluetooth.BluetoothReceiver]
         * as a pre-flight check before attempting to start this service.
         */
        fun hasCapturePermissions(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
            return fine && background
        }
    }
}

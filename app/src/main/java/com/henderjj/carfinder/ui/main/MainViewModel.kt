package com.henderjj.carfinder.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.henderjj.carfinder.data.AppPreferences
import com.henderjj.carfinder.data.ParkingLocation
import com.henderjj.carfinder.sensors.CompassAccuracy
import com.henderjj.carfinder.sensors.CompassReader
import com.henderjj.carfinder.sensors.CompassReading
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class MainUiState(
    val parking: ParkingLocation? = null,
    val userLocation: Location? = null,
    /** Heading in degrees clockwise from *true* north (declination applied). */
    val azimuthDegrees: Float = 0f,
    val compassAccuracy: CompassAccuracy = CompassAccuracy.UNKNOWN
) {
    val distanceMeters: Double?
        get() {
            val p = parking ?: return null
            val u = userLocation ?: return null
            val result = FloatArray(1)
            Location.distanceBetween(u.latitude, u.longitude, p.latitude, p.longitude, result)
            return result[0].toDouble()
        }

    val bearingToCarDegrees: Float?
        get() {
            val p = parking ?: return null
            val u = userLocation ?: return null
            return bearingDegrees(u.latitude, u.longitude, p.latitude, p.longitude)
        }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences.get(app)
    private val compass = CompassReader(app)
    private val fused = LocationServices.getFusedLocationProviderClient(app)

    private val userLocation = MutableStateFlow<Location?>(null)

    // Magnetic declination is location-dependent; cache it and only recompute when
    // the user moves enough to matter (~0.1°), since readings arrive ~50×/second.
    private var declination = 0f
    private var declinationKey = Long.MIN_VALUE

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { userLocation.value = it }
        }
    }

    val uiState: StateFlow<MainUiState> = combine(
        prefs.parking,
        userLocation,
        // Seed a reading so the screen still renders on devices with no compass.
        compass.readings().onStart { emit(CompassReading(0f, CompassAccuracy.UNKNOWN)) }
    ) { parking, user, reading ->
        val trueNorth = (reading.azimuthDegrees + declinationFor(user)).mod(360f)
        MainUiState(
            parking = parking,
            userLocation = user,
            azimuthDegrees = trueNorth,
            compassAccuracy = reading.accuracy
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    /** Local magnetic declination (degrees east) used to convert magnetic → true north. */
    private fun declinationFor(loc: Location?): Float {
        if (loc == null) return declination
        val key = (loc.latitude * 10).toLong() * 100_000L + (loc.longitude * 10).toLong()
        if (key != declinationKey) {
            declination = GeomagneticField(
                loc.latitude.toFloat(),
                loc.longitude.toFloat(),
                loc.altitude.toFloat(),
                if (loc.time != 0L) loc.time else System.currentTimeMillis()
            ).declination
            declinationKey = key
        }
        return declination
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasFineLocation()) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateDistanceMeters(1f)
            .build()
        fused.requestLocationUpdates(req, locationCallback, getApplication<Application>().mainLooper)
    }

    fun stopLocationUpdates() {
        fused.removeLocationUpdates(locationCallback)
    }

    @SuppressLint("MissingPermission")
    fun saveParkingNow() {
        if (!hasFineLocation()) return
        viewModelScope.launch {
            val loc = fused.lastLocation.await() ?: return@launch
            prefs.saveParking(
                ParkingLocation(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 0f,
                    capturedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearParking() {
        viewModelScope.launch { prefs.clearParking() }
    }

    fun updateParkingDetails(level: String?, section: String?, spaceNumber: String?) {
        viewModelScope.launch { prefs.updateParkingDetails(level, section, spaceNumber) }
    }

    fun clearPairedCar() {
        viewModelScope.launch { prefs.clearPairedCar() }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        stopLocationUpdates()
    }
}

/** Initial bearing in degrees clockwise from north (0..360). */
private fun bearingDegrees(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val φ1 = Math.toRadians(lat1)
    val φ2 = Math.toRadians(lat2)
    val λ1 = Math.toRadians(lng1)
    val λ2 = Math.toRadians(lng2)
    val y = sin(λ2 - λ1) * cos(φ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(λ2 - λ1)
    val θ = atan2(y, x)
    return ((Math.toDegrees(θ) + 360.0) % 360.0).toFloat()
}


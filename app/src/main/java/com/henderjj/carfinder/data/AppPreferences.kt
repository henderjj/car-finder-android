package com.henderjj.carfinder.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "car_finder")

data class ParkingLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtEpochMillis: Long,
    val level: String? = null,
    val section: String? = null,
    val spaceNumber: String? = null
) {
    val hasAnyDetails: Boolean
        get() = !level.isNullOrBlank() || !section.isNullOrBlank() || !spaceNumber.isNullOrBlank()
}

data class PairedCar(
    val address: String,
    val name: String
)

class AppPreferences private constructor(private val context: Context) {

    val parking: Flow<ParkingLocation?> = context.dataStore.data.map { p ->
        val lat = p[KEY_LAT] ?: return@map null
        val lng = p[KEY_LNG] ?: return@map null
        ParkingLocation(
            latitude = lat,
            longitude = lng,
            accuracyMeters = p[KEY_ACC] ?: 0f,
            capturedAtEpochMillis = p[KEY_TIME] ?: 0L,
            level = p[KEY_LEVEL]?.takeIf { it.isNotBlank() },
            section = p[KEY_SECTION]?.takeIf { it.isNotBlank() },
            spaceNumber = p[KEY_SPACE]?.takeIf { it.isNotBlank() }
        )
    }

    val pairedCar: Flow<PairedCar?> = context.dataStore.data.map { p ->
        val addr = p[KEY_CAR_ADDR] ?: return@map null
        PairedCar(address = addr, name = p[KEY_CAR_NAME].orEmpty())
    }

    suspend fun saveParking(loc: ParkingLocation) {
        context.dataStore.edit { p ->
            p[KEY_LAT] = loc.latitude
            p[KEY_LNG] = loc.longitude
            p[KEY_ACC] = loc.accuracyMeters
            p[KEY_TIME] = loc.capturedAtEpochMillis
            // saveParking is used by both the manual button and the BT-triggered service,
            // both of which mean "I've just parked here" — so clear any stale details
            // from a previous parking spot.
            p.remove(KEY_LEVEL)
            p.remove(KEY_SECTION)
            p.remove(KEY_SPACE)
        }
    }

    suspend fun updateParkingDetails(level: String?, section: String?, spaceNumber: String?) {
        context.dataStore.edit { p ->
            putOrRemove(p, KEY_LEVEL, level)
            putOrRemove(p, KEY_SECTION, section)
            putOrRemove(p, KEY_SPACE, spaceNumber)
        }
    }

    suspend fun clearParking() {
        context.dataStore.edit { p ->
            p.remove(KEY_LAT)
            p.remove(KEY_LNG)
            p.remove(KEY_ACC)
            p.remove(KEY_TIME)
            p.remove(KEY_LEVEL)
            p.remove(KEY_SECTION)
            p.remove(KEY_SPACE)
        }
    }

    suspend fun savePairedCar(car: PairedCar) {
        context.dataStore.edit { p ->
            p[KEY_CAR_ADDR] = car.address
            p[KEY_CAR_NAME] = car.name
        }
    }

    suspend fun clearPairedCar() {
        context.dataStore.edit { p ->
            p.remove(KEY_CAR_ADDR)
            p.remove(KEY_CAR_NAME)
        }
    }

    private fun putOrRemove(
        p: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String?
    ) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) p.remove(key) else p[key] = trimmed
    }

    companion object {
        private val KEY_LAT = doublePreferencesKey("parking_lat")
        private val KEY_LNG = doublePreferencesKey("parking_lng")
        private val KEY_ACC = floatPreferencesKey("parking_acc")
        private val KEY_TIME = longPreferencesKey("parking_time")
        private val KEY_LEVEL = stringPreferencesKey("parking_level")
        private val KEY_SECTION = stringPreferencesKey("parking_section")
        private val KEY_SPACE = stringPreferencesKey("parking_space")
        private val KEY_CAR_ADDR = stringPreferencesKey("car_addr")
        private val KEY_CAR_NAME = stringPreferencesKey("car_name")

        // Holds only the Application context (see get()), so it cannot leak an Activity.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: AppPreferences? = null
        fun get(context: Context): AppPreferences =
            instance ?: synchronized(this) {
                instance ?: AppPreferences(context.applicationContext).also { instance = it }
            }
    }
}

package com.henderjj.carfinder.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlin.math.abs

/** Magnetometer accuracy, surfaced so the UI can prompt for calibration. */
enum class CompassAccuracy { HIGH, MEDIUM, LOW, UNRELIABLE, UNKNOWN }

/** A single compass sample: heading plus the live sensor accuracy. */
data class CompassReading(
    /** Azimuth in degrees clockwise from *magnetic* north (0..360). */
    val azimuthDegrees: Float,
    val accuracy: CompassAccuracy
)

/**
 * Emits the device heading from the fused ROTATION_VECTOR sensor.
 *
 * The azimuth is relative to **magnetic** north — callers that need true north
 * must add the local magnetic declination (see [android.hardware.GeomagneticField]).
 *
 * Each reading is:
 *  - **display-rotation aware** — remapped so 0° always means "the top of the
 *    screen points north", even when the activity is in landscape;
 *  - **pitch aware** — when the phone is held upright (portrait, pointing at the
 *    car) the camera axis drives the heading; when laid flat (map-reading pose)
 *    the screen's top edge does. The two agree as you tilt between them, avoiding
 *    the gimbal-lock jitter a single fixed remap suffers near flat/vertical;
 *  - **low-pass filtered** — a circular exponential filter removes sensor jitter
 *    while staying responsive.
 */
class CompassReader(private val context: Context) {

    fun readings(): Flow<CompassReading> = callbackFlow {
        val sm = context.getSystemService<SensorManager>()
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sm == null || sensor == null) {
            close()
            return@callbackFlow
        }
        @Suppress("DEPRECATION")
        val display = context.getSystemService<WindowManager>()?.defaultDisplay

        val rotMatrix = FloatArray(9)
        val screenMatrix = FloatArray(9)
        val uprightMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothed = Float.NaN
        var accuracy = CompassAccuracy.UNKNOWN

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)

                // 1) Align to the screen so "up" on the Canvas reads as north on screen.
                val axisX: Int
                val axisY: Int
                when (display?.rotation) {
                    Surface.ROTATION_90 -> { axisX = SensorManager.AXIS_Y; axisY = SensorManager.AXIS_MINUS_X }
                    Surface.ROTATION_180 -> { axisX = SensorManager.AXIS_MINUS_X; axisY = SensorManager.AXIS_MINUS_Y }
                    Surface.ROTATION_270 -> { axisX = SensorManager.AXIS_MINUS_Y; axisY = SensorManager.AXIS_X }
                    else -> { axisX = SensorManager.AXIS_X; axisY = SensorManager.AXIS_Y }
                }
                SensorManager.remapCoordinateSystem(rotMatrix, axisX, axisY, screenMatrix)

                // 2) Choose the heading axis from device tilt. screenMatrix[8] is the
                // vertical component of the screen-normal axis: ~1 when flat, ~0 when
                // upright. Below the threshold the phone is "upright", so use the camera
                // axis (AXIS_Z) — this is continuous with the flat reading as you tilt.
                val matrix = if (abs(screenMatrix[8]) < FLAT_THRESHOLD) {
                    SensorManager.remapCoordinateSystem(
                        screenMatrix,
                        SensorManager.AXIS_X,
                        SensorManager.AXIS_Z,
                        uprightMatrix
                    )
                    uprightMatrix
                } else {
                    screenMatrix
                }

                SensorManager.getOrientation(matrix, orientation)
                val deg = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()

                smoothed = if (smoothed.isNaN()) deg else lowPass(smoothed, deg)
                trySend(CompassReading(smoothed, accuracy))
            }

            override fun onAccuracyChanged(sensor: Sensor?, value: Int) {
                accuracy = value.toCompassAccuracy()
            }
        }

        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sm.unregisterListener(listener) }
    }.conflate()
}

/** cos(~63°): below this the phone is treated as "upright" rather than "flat". */
private const val FLAT_THRESHOLD = 0.45f

/** Smoothing factor for the low-pass filter: higher = snappier but noisier. */
private const val ALPHA = 0.30f

/** Exponential low-pass over the shortest arc, so 359°→1° eases the short way. */
private fun lowPass(prev: Float, next: Float): Float {
    val delta = ((next - prev + 540f) % 360f) - 180f
    return (prev + ALPHA * delta + 360f) % 360f
}

private fun Int.toCompassAccuracy(): CompassAccuracy = when (this) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
    else -> CompassAccuracy.UNRELIABLE // SENSOR_STATUS_UNRELIABLE (0) and NO_CONTACT (-1)
}

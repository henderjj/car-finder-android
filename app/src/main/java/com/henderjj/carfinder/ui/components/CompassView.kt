package com.henderjj.carfinder.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val CARDINALS = listOf("N" to 0.0, "E" to 90.0, "S" to 180.0, "W" to 270.0)

/**
 * Visual compass.
 *
 * - The compass rose (cardinal letters + tick marks) is **world-aligned**: the
 *   N letter always points at real-world north, regardless of how the phone is held.
 *   This makes the letters meaningful: at a glance you can see which way north
 *   (and east/south/west) lies relative to the direction you're facing.
 *
 * - The needle points at the car, measured **relative to the direction the phone
 *   is facing**. If the needle points up, the car is straight ahead. If it points
 *   left, the car is to your left, and so on.
 *
 * Together: the user can both see the car's direction (arrow) and orient
 * themselves to the map (cardinals).
 *
 * The rose and needle animate along the shortest arc to each new value, so the
 * compass glides rather than snapping (and never spins the long way at 359°→1°).
 */
@Composable
fun CompassView(
    bearingToCarDegrees: Float,
    deviceAzimuthDegrees: Float,
    modifier: Modifier = Modifier,
    needsCalibration: Boolean = false
) {
    val ringColor = MaterialTheme.colorScheme.outline
    val arrowColor = MaterialTheme.colorScheme.secondary
    val centreColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurface
    val northColor = MaterialTheme.colorScheme.error // distinguish N

    // Animate the angles, not the derived screen positions, so both the rose
    // (driven by azimuth) and the needle (azimuth + bearing) stay smooth.
    val animatedAzimuth = animatedAngle(deviceAzimuthDegrees)
    val animatedBearing = animatedAngle(bearingToCarDegrees)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(labelColor) {
        TextStyle(color = labelColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    val northStyle = remember(northColor) {
        TextStyle(color = northColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }

    Box(modifier = modifier.padding(8.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f - 8f

            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(width = 4f)
            )

            // Tick marks — rotate with device so they sit at world-aligned angles.
            for (i in 0 until 12) {
                val worldDeg = i * 30.0
                val screenRad = Math.toRadians(worldDeg - animatedAzimuth)
                val inner = radius - if (i % 3 == 0) 18f else 10f
                val sx = center.x + (radius * sin(screenRad)).toFloat()
                val sy = center.y - (radius * cos(screenRad)).toFloat()
                val ex = center.x + (inner * sin(screenRad)).toFloat()
                val ey = center.y - (inner * cos(screenRad)).toFloat()
                drawLine(
                    color = ringColor,
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = 3f
                )
            }

            // Cardinal letters — placed at their world-aligned screen positions,
            // but the glyphs themselves stay upright so they're always readable.
            val labelRadius = radius * 0.68f
            CARDINALS.forEach { (text, worldDeg) ->
                val screenRad = Math.toRadians(worldDeg - animatedAzimuth)
                val tx = center.x + (labelRadius * sin(screenRad)).toFloat()
                val ty = center.y - (labelRadius * cos(screenRad)).toFloat()
                val style = if (text == "N") northStyle else labelStyle
                val measured = textMeasurer.measure(AnnotatedString(text), style)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        tx - measured.size.width / 2f,
                        ty - measured.size.height / 2f
                    )
                )
            }

            // Needle — points at the car, drawn relative to the phone.
            val needleRad = Math.toRadians(
                (animatedBearing - animatedAzimuth).toDouble()
            )
            val tip = Offset(
                center.x + (radius * 0.78f * sin(needleRad)).toFloat(),
                center.y - (radius * 0.78f * cos(needleRad)).toFloat()
            )
            val leftAngle = needleRad + Math.toRadians(150.0)
            val rightAngle = needleRad - Math.toRadians(150.0)
            val arrowSize = radius * 0.22f
            val left = Offset(
                tip.x + (arrowSize * sin(leftAngle)).toFloat(),
                tip.y - (arrowSize * cos(leftAngle)).toFloat()
            )
            val right = Offset(
                tip.x + (arrowSize * sin(rightAngle)).toFloat(),
                tip.y - (arrowSize * cos(rightAngle)).toFloat()
            )
            val path = Path().apply {
                moveTo(tip.x, tip.y); lineTo(left.x, left.y); lineTo(center.x, center.y)
                lineTo(right.x, right.y); close()
            }
            drawPath(path, color = arrowColor)

            drawCircle(color = Color.White, radius = 6f, center = center)
            drawCircle(color = centreColor, radius = 6f, center = center, style = Stroke(width = 2f))
        }

        // Calibration prompt — shown when the magnetometer reports low/unreliable
        // accuracy, which is when the heading drifts (near metal, after a knock).
        if (needsCalibration) {
            Text(
                text = "⚠ Calibrate\nmove in a figure-8",
                color = northColor,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            )
        }
    }
}

/**
 * Animates [target] (degrees) along the shortest arc. Tracks an unwrapped angle so
 * a 359°→1° change eases +2° rather than spinning -358° the long way round.
 *
 * Uses a spring rather than a fixed-duration tween: the heading updates many times a
 * second while you turn, and a tween would restart its ease from the current (lagging)
 * position on every update, so the needle could never catch up and felt sluggish. A
 * spring re-targets smoothly mid-flight (it carries its velocity across re-targets),
 * so it tracks a moving heading and settles quickly once you stop.
 */
@Composable
private fun animatedAngle(target: Float): Float {
    val anim = remember { Animatable(target) }
    LaunchedEffect(target) {
        anim.animateTo(
            targetValue = anim.value + shortestArcDelta(anim.value, target),
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    return anim.value
}

/**
 * Signed shortest-arc delta in degrees from [from] to [to], result in [-180, 180).
 *
 * Uses a floored modulo so it stays correct when [from] has been unwrapped past 360°
 * by repeated same-direction turns. The naive `(d + 540) % 360 - 180` form relies on
 * the operands staying in [0, 360): Kotlin's `%` keeps the dividend's sign, so once
 * `from` accumulates large enough that `to - from + 540` goes negative, that form
 * yields a delta outside [-180, 180] and the needle spins the long way round.
 */
private fun shortestArcDelta(from: Float, to: Float): Float {
    val wrapped = (((to - from + 180f) % 360f) + 360f) % 360f
    return wrapped - 180f
}

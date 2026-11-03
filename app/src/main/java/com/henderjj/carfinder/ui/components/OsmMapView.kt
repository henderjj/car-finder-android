package com.henderjj.carfinder.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.henderjj.carfinder.R
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker

private const val MAX_FIT_ZOOM = 20.0
private const val DEFAULT_CAR_ZOOM = 20.0

@Composable
fun OsmMapView(
    carLat: Double,
    carLng: Double,
    userLat: Double?,
    userLng: Double?,
    modifier: Modifier = Modifier
) {
    val carPoint = remember(carLat, carLng) { GeoPoint(carLat, carLng) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                controller.setZoom(DEFAULT_CAR_ZOOM)
                controller.setCenter(carPoint)
            }
        },
        update = { map ->
            map.overlays.clear()

            // OSM tile usage policy / ODbL require visible "© OpenStreetMap contributors"
            // attribution. Pinned to the top since the compass floats over the bottom.
            map.overlays.add(CopyrightOverlay(map.context).apply { setAlignBottom(false) })

            // Car: keep the default red pin — it reads as "destination".
            val carMarker = Marker(map).apply {
                position = carPoint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Car"
            }
            map.overlays.add(carMarker)

            if (userLat != null && userLng != null) {
                val userPoint = GeoPoint(userLat, userLng)
                val userMarker = Marker(map).apply {
                    position = userPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    icon = ContextCompat.getDrawable(map.context, R.drawable.ic_user_dot)
                    title = "You"
                }
                map.overlays.add(userMarker)

                // Fit both points, but cap the zoom so two close points don't push
                // the map to a blocky max-zoom view.
                val box = BoundingBox.fromGeoPoints(listOf(carPoint, userPoint))
                    .increaseByScale(1.4f)
                map.post {
                    map.zoomToBoundingBox(box, true, 80, MAX_FIT_ZOOM, 800L)
                }
            } else {
                map.controller.animateTo(carPoint)
            }

            map.invalidate()
        },
        onRelease = { it.onDetach() }
    )
}

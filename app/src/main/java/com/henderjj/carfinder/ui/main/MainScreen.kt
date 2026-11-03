package com.henderjj.carfinder.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.henderjj.carfinder.R
import com.henderjj.carfinder.sensors.CompassAccuracy
import com.henderjj.carfinder.ui.components.CompassView
import com.henderjj.carfinder.ui.components.OsmMapView
import java.text.DateFormat
import java.util.Date

@Composable
fun MainScreen(
    onChangeCar: () -> Unit,
    openDetailsRequested: Boolean = false,
    onDetailsConsumed: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showDetailsDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openDetailsRequested) {
        if (openDetailsRequested) {
            showDetailsDialog = true
            onDetailsConsumed()
        }
    }

    if (showDetailsDialog) {
        val current = state.parking
        ParkingDetailsDialog(
            initialLevel = current?.level,
            initialSection = current?.section,
            initialSpace = current?.spaceNumber,
            onSave = { lvl, sec, sp ->
                viewModel.updateParkingDetails(lvl, sec, sp)
                showDetailsDialog = false
            },
            onDismiss = { showDetailsDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val parking = state.parking
        if (parking == null) {
            EmptyStateCard()
            Spacer(modifier = Modifier.weight(1f))
        } else {
            // Map fills the available vertical space, with the compass floating
            // inside the bottom of the card.
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OsmMapView(
                        carLat = parking.latitude,
                        carLng = parking.longitude,
                        userLat = state.userLocation?.latitude,
                        userLng = state.userLocation?.longitude
                    )

                    CompassOverlay(
                        bearingToCarDegrees = state.bearingToCarDegrees ?: 0f,
                        deviceAzimuthDegrees = state.azimuthDegrees,
                        needsCalibration = state.compassAccuracy == CompassAccuracy.LOW ||
                            state.compassAccuracy == CompassAccuracy.UNRELIABLE,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }

            DistanceAndTimestampRow(
                distanceMeters = state.distanceMeters,
                capturedAtEpochMillis = parking.capturedAtEpochMillis
            )

            ParkingDetailsRow(
                level = parking.level,
                section = parking.section,
                spaceNumber = parking.spaceNumber,
                onClick = { showDetailsDialog = true }
            )
        }

        Button(
            onClick = { viewModel.saveParkingNow() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.main_save_now))
        }

        if (state.parking != null) {
            OutlinedButton(
                onClick = { viewModel.clearParking() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.main_clear))
            }
        }

        OutlinedButton(onClick = onChangeCar, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.main_change_car))
        }
    }
}

@Composable
private fun CompassOverlay(
    bearingToCarDegrees: Float,
    deviceAzimuthDegrees: Float,
    needsCalibration: Boolean,
    modifier: Modifier = Modifier
) {
    // Slightly translucent surface tinted with the theme, with elevation, so the
    // compass reads clearly over any map content beneath it.
    Surface(
        modifier = modifier.clip(CircleShape),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        CompassView(
            bearingToCarDegrees = bearingToCarDegrees,
            deviceAzimuthDegrees = deviceAzimuthDegrees,
            needsCalibration = needsCalibration,
            modifier = Modifier.size(180.dp)
        )
    }
}

@Composable
private fun DistanceAndTimestampRow(
    distanceMeters: Double?,
    capturedAtEpochMillis: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (distanceMeters == null) {
                Text(
                    text = "Waiting for your location…",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val label = if (distanceMeters < 1_000)
                    stringResourceFormat(R.string.main_distance_metres, distanceMeters)
                else
                    stringResourceFormat(R.string.main_distance_km, distanceMeters / 1_000)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = label,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "to your car",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }

        Text(
            text = androidx.compose.ui.res.stringResource(
                R.string.main_saved_at,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(capturedAtEpochMillis))
            ),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ParkingDetailsRow(
    level: String?,
    section: String?,
    spaceNumber: String?,
    onClick: () -> Unit
) {
    val parts = buildList {
        if (!level.isNullOrBlank())
            add(androidx.compose.ui.res.stringResource(R.string.details_level_prefix, level))
        if (!section.isNullOrBlank())
            add(androidx.compose.ui.res.stringResource(R.string.details_section_prefix, section))
        if (!spaceNumber.isNullOrBlank())
            add(androidx.compose.ui.res.stringResource(R.string.details_space_prefix, spaceNumber))
    }
    val hasAny = parts.isNotEmpty()

    AssistChip(
        onClick = onClick,
        label = {
            Text(
                if (hasAny) parts.joinToString("  ·  ")
                else androidx.compose.ui.res.stringResource(R.string.main_details_add)
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize)
            )
        }
    )
}

@Composable
private fun EmptyStateCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                androidx.compose.ui.res.stringResource(R.string.main_no_location_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                androidx.compose.ui.res.stringResource(R.string.main_no_location_subtitle),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun stringResourceFormat(id: Int, arg: Double): String =
    androidx.compose.ui.res.stringResource(id, arg.toFloat())

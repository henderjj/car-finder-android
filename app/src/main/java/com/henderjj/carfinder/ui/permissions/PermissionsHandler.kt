package com.henderjj.carfinder.ui.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.henderjj.carfinder.R

/**
 * In-screen permission gate, shown as a sequence of prompt cards until everything
 * the app needs is granted (or explicitly skipped where optional):
 *
 * 1. Fine location — required; the app is useless without it.
 * 2. Background location (Android 10+) — required for automatic capture: a
 *    location-typed foreground service may only be created from the background
 *    when the app holds ACCESS_BACKGROUND_LOCATION. Must be requested AFTER fine
 *    location, on its own; on Android 11+ the system sends the user to the
 *    settings screen where they pick "Allow all the time".
 * 3. Notifications (Android 13+) — optional; skippable.
 * 4. Battery-optimisation exemption — the FGS background-start exemption that
 *    makes the BT-disconnect capture legal to launch. Skippable, but automatic
 *    capture may be blocked by the system without it.
 *
 * Grants made on system Settings screens don't come back through an
 * ActivityResult callback, so all states are re-checked on every ON_RESUME.
 */
@Composable
fun LocationPermissionsGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var fineGranted by remember { mutableStateOf(hasFine(ctx)) }
    var backgroundGranted by remember { mutableStateOf(hasBackground(ctx)) }
    var notificationsGranted by remember { mutableStateOf(hasNotifications(ctx)) }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(ctx)) }

    var backgroundSkipped by rememberSaveable { mutableStateOf(false) }
    var notificationsSkipped by rememberSaveable { mutableStateOf(false) }
    var batterySkipped by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                fineGranted = hasFine(ctx)
                backgroundGranted = hasBackground(ctx)
                notificationsGranted = hasNotifications(ctx)
                batteryExempt = isBatteryExempt(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val fineLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> fineGranted = granted }

    val backgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> backgroundGranted = granted }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsGranted = granted }

    when {
        !fineGranted -> PermissionPrompt(
            title = stringResource(R.string.permission_location_rationale_title),
            body = stringResource(R.string.permission_location_rationale_body),
            primaryLabel = stringResource(R.string.permission_grant),
            onPrimary = { fineLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
            secondaryLabel = stringResource(R.string.permission_open_settings),
            onSecondary = { openAppSettings(ctx) }
        )

        !backgroundGranted && !backgroundSkipped -> PermissionPrompt(
            title = stringResource(R.string.permission_background_rationale_title),
            body = stringResource(R.string.permission_background_rationale_body),
            primaryLabel = stringResource(R.string.permission_grant),
            onPrimary = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                } else {
                    backgroundGranted = true
                }
            },
            // If the request was auto-denied (asked too many times), the launcher
            // shows nothing — the settings screen is the way out.
            secondaryLabel = stringResource(R.string.permission_open_settings),
            onSecondary = { openAppSettings(ctx) },
            tertiaryLabel = stringResource(R.string.permission_skip),
            onTertiary = { backgroundSkipped = true }
        )

        !notificationsGranted && !notificationsSkipped -> PermissionPrompt(
            title = stringResource(R.string.permission_notifications_rationale_title),
            body = stringResource(R.string.permission_notifications_rationale_body),
            primaryLabel = stringResource(R.string.permission_grant),
            onPrimary = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationsGranted = true
                }
            },
            secondaryLabel = stringResource(R.string.permission_skip),
            onSecondary = { notificationsSkipped = true }
        )

        !batteryExempt && !batterySkipped -> PermissionPrompt(
            title = stringResource(R.string.permission_battery_rationale_title),
            body = stringResource(R.string.permission_battery_rationale_body),
            primaryLabel = stringResource(R.string.permission_grant),
            onPrimary = { requestBatteryExemption(ctx) },
            secondaryLabel = stringResource(R.string.permission_skip),
            onSecondary = { batterySkipped = true }
        )

        else -> content()
    }
}

@Composable
private fun PermissionPrompt(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.size(4.dp))
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primaryLabel) }
        if (secondaryLabel != null && onSecondary != null) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryLabel)
            }
        }
        if (tertiaryLabel != null && onTertiary != null) {
            OutlinedButton(onClick = onTertiary, modifier = Modifier.fillMaxWidth()) {
                Text(tertiaryLabel)
            }
        }
    }
}

private fun hasFine(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

private fun hasBackground(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasNotifications(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun isBatteryExempt(ctx: Context): Boolean =
    ctx.getSystemService<PowerManager>()
        ?.isIgnoringBatteryOptimizations(ctx.packageName) == true

// Suppressed: ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is restricted on the Play
// Store for most app categories, but this is a sideloaded utility whose core feature
// (background parking capture on BT disconnect) is exactly what the exemption exists for.
@SuppressLint("BatteryLife")
private fun requestBatteryExemption(ctx: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.fromParts("package", ctx.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    ctx.startActivity(intent)
}

private fun openAppSettings(ctx: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", ctx.packageName, null)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    ctx.startActivity(intent)
}

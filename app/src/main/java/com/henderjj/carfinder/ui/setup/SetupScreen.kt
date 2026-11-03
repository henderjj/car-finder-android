package com.henderjj.carfinder.ui.setup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.henderjj.carfinder.R

@Composable
fun SetupScreen(
    onPaired: () -> Unit,
    viewModel: SetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium
        )

        if (!state.hasBluetoothPermission) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.setup_grant_bluetooth))
            }
            return@Column
        }

        if (state.devices.isEmpty()) {
            Text(
                text = stringResource(R.string.setup_no_devices),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.setup_open_settings)) }
            OutlinedButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.setup_refresh)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.devices, key = { it.address }) { device ->
                    DeviceRow(device = device, onClick = { viewModel.choose(device, onPaired) })
                }
            }
            OutlinedButton(
                onClick = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.setup_refresh)) }
        }
    }
}

@Composable
private fun DeviceRow(device: BondedDevice, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (device.likelyCar) Icons.Filled.DirectionsCar else Icons.Filled.Bluetooth,
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            if (device.likelyCar) {
                Text(
                    text = "likely car",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

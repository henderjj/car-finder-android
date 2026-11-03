package com.henderjj.carfinder.ui.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.henderjj.carfinder.data.AppPreferences
import com.henderjj.carfinder.data.PairedCar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BondedDevice(
    val address: String,
    val name: String,
    val likelyCar: Boolean
)

data class SetupUiState(
    val hasBluetoothPermission: Boolean = false,
    val devices: List<BondedDevice> = emptyList()
)

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences.get(app)
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else true
        _state.value = _state.value.copy(
            hasBluetoothPermission = granted,
            devices = if (granted) bondedDevices() else emptyList()
        )
    }

    @SuppressLint("MissingPermission")
    private fun bondedDevices(): List<BondedDevice> {
        val ctx = getApplication<Application>()
        val adapter: BluetoothAdapter? = ctx.getSystemService<BluetoothManager>()?.adapter
        val bonded = adapter?.bondedDevices ?: return emptyList()
        return bonded.map { d ->
            val major = d.bluetoothClass?.majorDeviceClass
            val likelyCar = major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    d.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO ||
                    d.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            BondedDevice(
                address = d.address,
                name = d.name ?: d.address,
                likelyCar = likelyCar == true
            )
        }.sortedWith(compareByDescending<BondedDevice> { it.likelyCar }.thenBy { it.name })
    }

    fun choose(device: BondedDevice, onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.savePairedCar(PairedCar(address = device.address, name = device.name))
            onDone()
        }
    }
}

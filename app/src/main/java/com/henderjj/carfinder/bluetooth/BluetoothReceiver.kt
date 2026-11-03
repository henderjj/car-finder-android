package com.henderjj.carfinder.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.henderjj.carfinder.data.AppPreferences
import com.henderjj.carfinder.location.ParkingCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return

        @Suppress("DEPRECATION")
        val device: BluetoothDevice? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        val address = device?.address ?: return

        // Async match against the paired car, then start the capture service.
        // goAsync() lets the receiver outlive onReceive() while we read DataStore.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val paired = AppPreferences.get(context).pairedCar.first()
                if (paired == null || !paired.address.equals(address, ignoreCase = true)) {
                    Log.d(TAG, "Ignoring disconnect from $address (paired=${paired?.address})")
                } else if (!ParkingCaptureService.hasCapturePermissions(context)) {
                    // Without fine + background location the location-typed foreground
                    // service is not allowed to start from the background (Android 14+
                    // throws); don't even attempt it.
                    Log.w(TAG, "Paired car disconnected but location permissions are incomplete — skipping capture")
                } else {
                    Log.i(TAG, "Paired car disconnected ($address) — starting capture")
                    ParkingCaptureService.start(context)
                }
            } catch (e: Exception) {
                // Never crash the process from here. ForegroundServiceStartNotAllowedException /
                // SecurityException just mean the system refused this capture attempt.
                Log.e(TAG, "Automatic parking capture failed to start", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "CarFinder.BT"
    }
}

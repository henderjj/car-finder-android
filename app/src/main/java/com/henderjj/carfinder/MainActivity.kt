package com.henderjj.carfinder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.henderjj.carfinder.data.AppPreferences
import com.henderjj.carfinder.ui.main.MainScreen
import com.henderjj.carfinder.ui.main.MainViewModel
import com.henderjj.carfinder.ui.permissions.LocationPermissionsGate
import com.henderjj.carfinder.ui.setup.SetupScreen
import com.henderjj.carfinder.ui.theme.CarFinderTheme
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {

    // True when the launching intent requested that the parking-details dialog
    // be opened immediately. Reset to false once the UI has consumed it.
    private var openDetailsRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            // Only consume the launch intent once per process. On config-change
            // recreation we rely on the saved Compose state to keep the dialog open.
            consumeIntentExtras(intent)
        }
        setContent {
            CarFinderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    LocationPermissionsGate {
                        Root(
                            openDetailsRequested = openDetailsRequested,
                            onDetailsConsumed = { openDetailsRequested = false },
                            modifier = Modifier.fillMaxSize().padding(padding)
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntentExtras(intent)
    }

    private fun consumeIntentExtras(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DETAILS, false) == true) {
            openDetailsRequested = true
            intent.removeExtra(EXTRA_OPEN_DETAILS)
        }
    }

    companion object {
        const val EXTRA_OPEN_DETAILS = "com.henderjj.carfinder.OPEN_DETAILS"
    }
}

@Composable
private fun Root(
    openDetailsRequested: Boolean,
    onDetailsConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pairedFlow: kotlinx.coroutines.flow.Flow<Boolean?> = remember {
        AppPreferences.get(context).pairedCar.map { it != null }
    }
    val isPaired: Boolean? by pairedFlow.collectAsStateWithLifecycle(initialValue = null)

    // Saveable so "Change paired car" survives rotation on large screens.
    var forceSetup by rememberSaveable { mutableStateOf(false) }

    when (isPaired) {
        null -> Unit // still loading from DataStore
        false -> SetupScreen(onPaired = { forceSetup = false })
        true -> {
            if (forceSetup) {
                SetupScreen(onPaired = { forceSetup = false })
            } else {
                MainWithLocationUpdates(
                    onChangeCar = { forceSetup = true },
                    openDetailsRequested = openDetailsRequested,
                    onDetailsConsumed = onDetailsConsumed,
                    modifier = modifier
                )
            }
        }
    }
}

@Composable
private fun MainWithLocationUpdates(
    onChangeCar: () -> Unit,
    openDetailsRequested: Boolean,
    onDetailsConsumed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: MainViewModel = viewModel()
    LaunchedEffect(Unit) { vm.startLocationUpdates() }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { vm.stopLocationUpdates() }
    }
    androidx.compose.foundation.layout.Box(modifier = modifier) {
        MainScreen(
            onChangeCar = onChangeCar,
            openDetailsRequested = openDetailsRequested,
            onDetailsConsumed = onDetailsConsumed,
            viewModel = vm
        )
    }
}

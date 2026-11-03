# Car Finder

[![CI](https://github.com/henderjj/car-finder-android/actions/workflows/ci.yml/badge.svg)](https://github.com/henderjj/car-finder-android/actions/workflows/ci.yml) [![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)

Saves your car's GPS location automatically when your phone disconnects from the car over Bluetooth, then helps you walk back to it using a map, compass, and live distance readout.

For end-user instructions (setup, day-to-day use, troubleshooting), see [USER_GUIDE.md](./USER_GUIDE.md). The notes below are for opening the project in Android Studio and understanding the layout.

## What it does

When your phone disconnects from the car's Bluetooth, the app fetches a GPS fix and saves it. The main screen shows that location on an OpenStreetMap tile, with a floating compass overlaid on the bottom of the map. The compass needle always points at the car; the N/E/S/W letters on the dial track real-world bearings (the dial rotates as the phone turns, so the letters stay meaningful). Distance to the car updates live as you walk. You can also save manually, and annotate the spot with car-park level / section / space details — editable via a chip on the main screen, or jumped to directly from the "Parking saved" notification's action button.

## Tech stack

- Kotlin 2.4 + Jetpack Compose (BOM 2026.09) + Material 3
- AGP 9.5 + Gradle 9.8 + JDK 17 (bytecode), Android Studio's bundled JBR for runtime
- compileSdk / targetSdk 37 (Android 17), minSdk 28 (Android 9)
- osmdroid 6.1 for the in-app map (no API key required)
- Google Play Services FusedLocationProviderClient for the GPS fix
- DataStore Preferences for persistence
- Manifest-registered `BroadcastReceiver` for `ACTION_ACL_DISCONNECTED` (exempt from Android 8's implicit-broadcast restrictions, so it fires without the app running). Starting the location-typed capture FGS from that receiver is only legal because the app holds `ACCESS_BACKGROUND_LOCATION` (the documented carve-out for creating a location FGS while in the background) and asks the user for a battery-optimisation exemption (a documented FGS background-start exemption)

## Opening the project

1. Clone the repository:
   ```bash
   git clone https://github.com/henderjj/car-finder-android.git
   ```
2. Launch Android Studio (Ladybug or newer), choose **File → Open** and select the `car-finder-android` folder. Studio writes a `local.properties` pointing at your Android SDK on first sync; that file is machine-specific and git-ignored.
3. The Gradle wrapper is committed and pins Gradle 9.8.2 (with a SHA-256 checksum of the distribution), so the first sync downloads and verifies the right Gradle version automatically.
4. **File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**: pick the **Embedded JDK** (`jbr-21`) or any installed JDK 17+. The project intentionally doesn't declare a Kotlin toolchain, so it inherits whatever JDK Studio launches Gradle with.
5. Plug in a real Android device (emulators can't pair with your car) and run **app**.

## Required runtime permissions

| Permission | Why |
|---|---|
| Fine location | Capture and display your position and the car's |
| Background location ("Allow all the time", Android 10+) | The capture runs while the app is in the background; Android 14+ refuses to even create a location-typed foreground service from the background without this grant |
| Bluetooth (Connect) | Read which paired device disconnected |
| Notifications (Android 13+) | Show the "Parking saved" confirmation and surface the "Add bay details" deep-link action |
| Battery-optimisation exemption | The documented exemption that lets the BT-disconnect receiver start the capture foreground service from the background (Android 12+ otherwise throws `ForegroundServiceStartNotAllowedException`) |

An earlier revision intentionally avoided `ACCESS_BACKGROUND_LOCATION`, assuming the foreground service's location type granted "while in use" access on its own. That assumption is wrong on Android 14+: a foreground service needing while-in-use permissions cannot be created while the app is in the background — even under a background-start exemption — and the attempt throws a `SecurityException` that crashed the app on every parking event. Holding `ACCESS_BACKGROUND_LOCATION` is the documented carve-out that makes the background-started location FGS legal.

## Project layout

```
app/src/main/java/com/henderjj/carfinder/
├── MainActivity.kt                       — entry point, nav (Setup ↔ Main), notification deep-link plumbing
├── CarFinderApp.kt                       — Application class, osmdroid + notification channel init
├── bluetooth/BluetoothReceiver.kt        — listens for ACL_DISCONNECTED, matches against paired car
├── location/ParkingCaptureService.kt     — foreground service that fetches a single GPS fix + posts notification
├── sensors/CompassReader.kt              — ROTATION_VECTOR sensor → azimuth flow
├── data/AppPreferences.kt                — DataStore: paired car + parking location + car-park details
└── ui/
    ├── setup/                            — first-run device picker
    ├── main/
    │   ├── MainScreen.kt                 — map + compass overlay + distance + details chip + buttons
    │   ├── MainViewModel.kt              — combines parking + user location + sensor into one state
    │   └── ParkingDetailsDialog.kt       — Level / Section / Space entry dialog
    ├── components/
    │   ├── CompassView.kt                — Canvas-drawn dial; world-aligned cardinals, needle relative to phone
    │   └── OsmMapView.kt                 — osmdroid MapView wrapped in an AndroidView
    └── permissions/PermissionsHandler.kt — runtime permission gate
```

## Notable design choices

- **Portrait-locked on phones** (`android:screenOrientation="portrait"`). It's a phone-first utility and landscape adds little. Since the app targets API 37, Android ignores the lock on large screens (smallest width ≥ 600dp: tablets, unfolded foldables), so the UI must also work in landscape and survive rotation — UI state that must persist across recreation (the details dialog, the "Change paired car" screen) uses `rememberSaveable`.
- **`launchMode="singleTop"`** so the "Add bay details" notification action can re-deliver to the running activity via `onNewIntent` rather than rebuilding it.
- **Manifest-registered BT receiver** rather than a long-running service. `ACTION_ACL_DISCONNECTED` is on the implicit-broadcast exemption list, so it still fires on Android 8+ without the app needing to be alive.
- **Background-location permission + battery-optimisation exemption.** Required for the automatic capture to be legal on modern Android: `ACCESS_BACKGROUND_LOCATION` lets the location-typed FGS be created from the background (Android 14+ throws otherwise), and the battery exemption is the FGS background-start exemption the receiver relies on. The receiver pre-flights these and skips the capture (logging a warning) rather than crashing when they're missing; the service additionally guards `startForeground` so a system refusal can never take down the process. Companion Device Manager would be the Google-blessed alternative that avoids the background-location grant — noted as a possible future rework.
- **No Kotlin toolchain declaration.** AGP 9's built-in Kotlin support is used; the project intentionally avoids requesting a JDK via `jvmToolchain(...)` because foojay auto-provisioning failed on the original dev machine (Windows file-lock / AV interaction). Re-add a `kotlin { jvmToolchain(17) }` block if you want auto-provisioning on a clean dev machine.
- **No Hilt / no DI framework.** The app has one Application-scoped singleton (`AppPreferences`) reached via a static `get(context)`. Adding DI for a five-screen utility wasn't worth the complexity.
- **No animation on the compass needle.** The needle and the world-aligned rose are both driven by the raw sensor value; animating only the needle would have it drift visibly out of sync with the rose during phone rotation, and `animateFloatAsState` doesn't understand angular wrap-around (it takes the long way around when relative bearing crosses ±180°).

## Building from the command line

Requires JDK 17+ on `JAVA_HOME` (Android Studio's bundled JBR works) and an Android SDK, located via `local.properties` or `ANDROID_HOME`.

```bash
./gradlew assembleDebug
./gradlew installDebug   # device must be connected with USB debugging on
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

## Continuous integration

[GitHub Actions](.github/workflows/ci.yml) runs on every push to `main` and every pull request: it validates the Gradle wrapper, builds the debug APK, and runs Android lint and unit tests. The debug APK and lint report are attached to each run as downloadable artifacts (kept for 14 days). To run the same checks locally:

```bash
./gradlew assembleDebug lintDebug testDebugUnitTest
```

## Known limitations

- **First-fix latency.** `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` typically returns in a few seconds, but on cold-start GPS this can stretch to 30+ seconds. The foreground service stays alive until the fix arrives or the system kills it.
- **OEM battery savers.** Samsung, Xiaomi, Huawei, OnePlus all kill background receivers aggressively. See the user guide for per-vendor allowlist instructions.
- **Bluetooth re-pairing.** The app stores the device's MAC address. If the car re-pairs and is assigned a new MAC (some BMW / Mercedes head units do this), the user has to re-pick the device from the setup screen.
- **Tile cache.** osmdroid caches tiles to the app's private external storage. First load near the car needs network; after that the area works offline.

## Privacy

Car Finder has no backend, analytics, or ads. The paired car's Bluetooth address, the saved parking location, and any car-park details are stored only in the app's on-device DataStore (included in Android's standard device backup). The app's own network traffic is limited to map-tile downloads from the OpenStreetMap tile servers; location fixes come from Google Play Services' fused location provider, which is subject to the device's Google location settings.

## License

Copyright 2026 henderjj. Licensed under the [Apache License, Version 2.0](./LICENSE) — see also [NOTICE](./NOTICE).

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), available under the Open Database License. Map tiles are served by the OpenStreetMap Foundation under its [tile usage policy](https://operations.osmfoundation.org/policies/tiles/); heavy or commercial use should switch to a dedicated tile provider.

# JKBMS-Android

[简体中文](README.md) | [English](README_EN.md)

JKBMS-Android is an unofficial Android client for JK BMS, built with Kotlin and Jetpack Compose. It connects directly to a battery management system over BLE and displays cell voltages, current, pack voltage, power, temperatures, SOC, alarms, and other status data without relying on the manufacturer's cloud service.

Current version: `1.2.8 (17)`. Minimum supported version: Android 8.1 (API 27).

See [CHANGELOG_EN.md](CHANGELOG_EN.md) for release history. The Chinese changelog is available in [CHANGELOG.md](CHANGELOG.md).

> This is not an official JK product. Protocol behavior may differ between hardware and firmware versions. Decisions involving vehicle or battery safety should be based on the BMS, vehicle instrumentation, and physical measurements.

## Features

- Scan for and connect directly to a JK BMS, verify its device password, and read live status data.
- View runtime, BLE status, GPS speed, the main power gauge, current, SOC, voltage, and board metrics on one dashboard.
- Keep the main gauge on Compose Canvas, with selectable voltage/current/power modes, bidirectional values, and a smoke animation.
- Record voltage, current, power, and SOC using bundled offline ECharts, with a rolling 60-second view and full-record playback.
- Combine GPS speed and BMS current to calculate riding efficiency, recovered charge, and estimated remaining range.
- Simulate acceleration, cruising, regenerative braking, urban speed changes, and stops with MOCK driving mode.
- Choose dark, light, or system theme, application font, speed card visibility, compact split-screen mode, and gauge ranges.
- Automatically reconnect after stale BLE data. While MOCK is active, real BMS frames are cached instead of competing with simulated UI data.
- Optionally keep energy collection active in the background with a foreground service, persistent notification, and partial wake lock.
- Switch the energy card among Chart, TRIP A, and TOTAL with one button in the upper-right corner. TOTAL distance and average efficiency persist across launches.
- Use a floating translucent capsule bottom navigation bar, and optionally hide the MOCK button on the home screen.

## Riding Efficiency

The energy tracker combines GPS speed with BMS current and uses trapezoidal integration against a monotonic clock. The current-direction convention used for JK BMS data in this project is:

- Negative current: discharge.
- Positive current: charging or regenerative braking while riding.

The calculations are:

```text
Discharged Ah = ∫ max(-current, 0) dt
Recovered Ah  = ∫ max( current, 0) dt
Net used Ah   = discharged Ah - recovered Ah
Distance km   = ∫ GPS speed dt
Efficiency km/Ah = distance ÷ net used Ah
Estimated range km = remaining BMS capacity Ah × valid efficiency km/Ah
```

The UI uses the two-wheel-friendly `1Ah Xkm` format. For example, `1Ah 3.0km` means the vehicle can travel approximately 3 km per 1 Ah consumed. Regeneration reduces net consumption and therefore improves the final `km/Ah` result.

### Time Ranges for the Line and Pie Charts

| View | Data range | Meaning |
| --- | --- | --- |
| Efficiency line | Latest 60 seconds | Rolling efficiency at about 2 Hz; old points leave from the left and MAX/MIN mark the current window |
| Discharge/recovery pie | Entire current trip | Orange is cumulative discharge and green is cumulative recovery; the recovery share is shown below |
| TRIP A | Current trip | Current distance, net consumption, trip efficiency, recovered charge, and estimated range |
| TOTAL | Persistent lifetime total | Total distance, total net consumption, long-term average efficiency, and cumulative recovery across app restarts |

The recovery share is:

```text
Recovery share = recovered Ah ÷ (discharged Ah + recovered Ah)
```

It is not the net-consumption share and is not limited to the line chart's 60-second window.

### Start, Stop, and Pause Rules

- A trip starts after GPS speed reaches `3km/h` continuously for 3 seconds.
- When GPS speed falls below `1km/h`, energy integration, accumulated consumption, the line chart, and the pie chart freeze immediately.
- After 15 seconds at low speed, the tracker enters a temporary-stop state, but a short stop does not split the trip.
- After 3 minutes without motion, the current trip ends. Its results remain visible until the next confirmed start.
- On restart, integration resumes from a new BMS frame and never backfills energy across the stopped interval.
- Losing GPS or stopping collection also breaks integration continuity. Unless Background Energy Collection is enabled, collection pauses when the app enters the background.

Accessory loads such as lights or controller standby consumption while parked are therefore excluded. This metric answers “how many Ah are needed to move the vehicle,” rather than auditing all static electrical loads.

### GPS Data Quality

Only location results containing speed are accepted. Current tolerances require approximately `60m` accuracy or better, speed uncertainty around `5m/s` or better, a location age no greater than `2.5s`, and a speed below `200km/h`. After GPS loss, the first valid fix only rebuilds the time baseline; integration resumes after a second consecutive fresh fix. This prevents a stopped or disconnected interval from being folded into a single sample. Energy collection can continue with the home speed card hidden as long as GPS remains enabled.

### Background Energy Collection

`Settings → Background energy collection` is off by default. Enabling it turns on GPS and starts a foreground service with a persistent notification. BLE polling, GPS updates, and energy integration are then kept active while the app is in the background. The service also holds a partial wake lock, which is useful during rides but increases battery usage.

Android 13 and later additionally request notification permission. The location foreground service is started while the app UI is visible, so the app does not request the separate “Allow all the time” background-location permission. The foreground service and its notification explicitly communicate ongoing collection to Android and the user. If notification permission is denied, Android may still show the service in its active-apps UI, but allowing notifications is recommended. Some vendor ROMs also require enabling autostart/background operation or disabling battery optimization for this app. Collection cannot continue after force-stopping the app or disabling Bluetooth/location.

### TRIP A and TOTAL

- `TRIP A` follows the trip state machine, resets at the next confirmed start, and records the current trip.
- `TOTAL` accumulates only while fresh GPS and BMS data are both available, preventing distance from increasing during a BLE outage.
- TOTAL is saved locally about every 5 seconds and during lifecycle transitions. MOCK data never enters TOTAL.
- `Settings → Energy panel` can hide the entire card. `Clear TOTAL` resets total distance, cumulative discharge, and cumulative recovery after confirmation without changing TRIP A.
- `Settings → Home MOCK button` can hide the test entry point. Hiding it also stops MOCK if it is active.

### Minimum Valid Samples

To avoid extreme results from a tiny denominator immediately after starting:

- Trip average efficiency requires about `0.1km` and `0.01Ah` net consumption.
- TOTAL average efficiency requires about `0.1km`.
- A 60-second trend point requires about `0.03km` and `0.003Ah`.

Estimated range prefers TRIP A efficiency and falls back to the more stable TOTAL average when the current trip does not yet have enough data. It is a live estimate based on history, not a prediction of terrain, wind, temperature, or riding style.

## BLE Data Path

- JK service UUID: `FFE0`; read/write/notify characteristic UUID: `FFE1`.
- Device information command: `0x97`; cell status command: `0x96`.
- Target status-frame polling interval: `500ms`.
- No duplicate request is sent while the previous request is pending. A retry is allowed after a `1.5s` timeout to prevent overlapping fragmented BLE responses.
- Approximately 3 seconds without data after authentication triggers reconnection. Returning to the foreground also restarts polling, RSSI updates, and the data watchdog.
- After disconnect, the old GATT is released and the app reconnects directly to the last successful device while foregrounded or background collection is enabled. Late callbacks from an old GATT cannot overwrite the new connection state.
- UI state and the energy tracker update only after a status frame passes CRC validation.

Faster polling improves gauge and chart responsiveness, but the actual update rate still depends on BMS firmware, connection interval, the phone's Bluetooth stack, and radio conditions.

## MOCK Mode

The floating home button starts a simulated ride through the same energy path as real BMS/GPS data. It covers:

- Hard acceleration and high-current discharge.
- High-speed cruising.
- Hard deceleration with positive-current regeneration.
- Variable urban speeds.
- Stops and restarts.

If the phone remains connected to a real BMS, MOCK prevents real and simulated data from alternating in the UI. Real frames are cached in the background, and the latest real data is restored when MOCK ends.

## Chart Implementation

Except for the main power gauge, line and pie charts are rendered by bundled ECharts Canvas:

- The ECharts script ships inside the APK and requires no network access.
- The one-minute window uses fixed slots and stable data-point IDs so retained data moves smoothly to the left.
- Updates do not repeatedly force `resize()`, which would interrupt ECharts transitions.
- General chart recording is capped at 7,200 BMS samples to prevent unbounded memory growth.

## Permissions and Data

The app may request:

- Nearby-device scan and connection permissions on Android 12 and later.
- Precise location for GPS speed and legacy Android BLE scanning requirements.
- Foreground-service and wake-lock permissions for background collection, plus notification permission on Android 13 and later.

The app uses only speed, accuracy, and timestamps from location results. It does not store route coordinates and needs no account or cloud service. The device password, last successful device address, TOTAL aggregates, and display settings are stored in local app preferences.

## Build

Use Android Studio's bundled JBR/JDK and the repository's Gradle Wrapper.

```powershell
# Windows PowerShell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug
```

```bash
# macOS / Linux
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The repository's officially signed build is located at:

```text
app/release/app-release.apk
```

Most users should download the officially signed APK from [GitHub Releases](https://github.com/mazemingw/JKBMS-Android/releases). Later releases signed with the same key can be installed as in-place updates.

To update an installation on a connected device:

```powershell
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
```

## Tests

`EnergyTrackerTest` covers:

- Ah, distance, and `km/Ah` integration under constant speed/current.
- A 15-second stop and resumed motion.
- Positive-current regeneration reducing net consumption.
- 500 ms trend sampling and the rolling one-minute window.
- Immediate freezing of trip, TOTAL, and line data after GPS reaches zero.
- No current backfill across a stopped interval.
- Rebuilding the integration baseline with two consecutive fresh fixes after an extended GPS interruption.
- Entering the temporary-stop state without increasing consumption while location is unavailable.

Run unit tests with:

```powershell
.\gradlew.bat testDebugUnitTest
```

## Project Structure

```text
app/src/main/java/com/yiming/jkbms/
├── MainActivity.kt                 BLE, GPS, MOCK, and UI orchestration
├── BackgroundCollectionService.kt Foreground service, notification, and wake lock
├── model/EnergyTracker.kt          Trip state, integration, and trend calculations
├── model/BmsModels.kt              BMS, energy, and UI data models
├── ui/components/EChartsLine.kt    Compose WebView/ECharts bridge
├── ui/screens/InfoScreen.kt        Dashboard gauge, energy card, and details
├── ui/screens/ChartScreen.kt       General trend recording and playback
└── viewmodel/MainViewModel.kt      Screen state and energy-tracker lifecycle

app/src/main/assets/echarts/
├── chart.html                      ECharts configuration and incremental animation
└── echarts.min.js                  Bundled ECharts runtime
```

## Known Limitations

- The parser targets the 300-byte JK BMS status frame tested by this project. Other protocol versions may require additional adaptation.
- GPS can be unreliable indoors, in tunnels, or among tall buildings. Efficiency is not calculated while positioning is unavailable.
- TRIP A is kept only for the current app process and is not yet stored in a historical trip database. TOTAL stores aggregate values only.
- The debug APK uses a debug signature. Configure a dedicated signing key and release build before production distribution.

## Third-Party Components

- [Apache ECharts](https://echarts.apache.org/) `6.1.0`, Apache License 2.0.
- AndroidX, Jetpack Compose, and Material Components under their respective open-source licenses.

# JKBMS-Android

[简体中文](README.md) · [English](README_EN.md)

JKBMS-Android is a third-party Android app for JK battery management systems. It reads battery data directly over Bluetooth: cell voltages, current, power, temperatures, remaining charge, and alarms. With GPS speed available, it can also estimate riding efficiency and remaining range. No account is needed, and settings and statistics stay on the device.

The current release is **1.3.0 (18)** and requires Android 8.1 or later. This release addresses disappearing navigation in compact mode and stalled Bluetooth sessions after returning from the background, and adds automatic recording, speed curves, and two configurable charts. See the [changelog](CHANGELOG_EN.md) for details.

## Installing and connecting

Download the APK from [GitHub Releases](https://github.com/mazemingw/JKBMS-Android/releases).

**Version 1.3.0 uses a new signing key.** If you have the official 1.2.8 release or an older debug build installed, uninstall it before installing this version. Uninstalling clears local settings and statistics, so save any information you need first. Future releases using the new key can be installed as updates.

Open the Bluetooth tab, allow nearby-device access, scan for your BMS, select it, and enter its device password. Older Android versions also require location access for Bluetooth scanning. GPS speed and efficiency estimates need precise location permission. The dashboard begins updating after the device password is verified.

The app remembers the last successful device and password and tries to reconnect on the next launch. If battery data stops arriving, stale readings are cleared and the connection is retried. During normal collection, about three seconds without a valid battery frame triggers recovery; connection setup, service discovery, and authentication have a fifteen-second timeout. Corrupt packets cannot keep old readings alive.

## Using the dashboard

The home page shows runtime, Bluetooth status, GPS speed, current, power, charge level, and pack voltage. Scroll down for alarms, board readings, and individual cell voltages. Values and units stay on the same line, with text scaled to fit narrower cards.

Tap the speed card to toggle GPS. Settings let you choose the main gauge, its source and range, the theme and font, and whether the speed and energy panels are visible.

The energy card opens with trip statistics. If a previous trip was saved, its distance, consumption, and efficiency appear immediately, even before the BMS reconnects. A newly confirmed ride replaces those values. Tap the upper-right label to cycle through TRIP A, TOTAL, and the recent efficiency curve. TRIP A covers one ride; TOTAL persists across launches. Clearing TOTAL asks for confirmation and preserves the current trip.

Compact information mode applies only when Android reports a multi-window session and the information page is open. Fullscreen phones keep their navigation. Compact mode also has a restore-navigation button and a system Back escape, so clearing app data is unnecessary.

The MOCK button generates acceleration, cruising, regeneration, stops, and restarts for trying the interface without a BMS. With GPS enabled, it supplies simulated speed too. Mock readings do not enter TOTAL or overwrite the saved real trip. You can hide the button in Settings.

## Recording and charts

Recording starts when battery data arrives; opening the chart page first is unnecessary. Voltage, current, power, charge level, and valid GPS speed are recorded. Speed can continue independently during a temporary BMS outage. Missing readings create visible gaps.

Two charts initially show voltage and speed. Tap either title to choose its source independently, or switch to a single chart. The layout and both selections are saved. Charts stack on a phone and sit side by side when the window is wide enough.

Live mode shows the latest sixty seconds. Full-record playback uses a snapshot taken when you enter playback, while collection continues. Pausing and resuming keeps existing samples. Up to 7,200 samples are retained in the current app process; curves are not saved as historical recordings after the process ends.

ECharts ships inside the APK and works offline. The main dashboard gauge uses Compose Canvas.

## Understanding efficiency

The tracker combines reliable GPS speed with BMS current. Integrating speed gives distance; integrating current gives discharged and recovered amp-hours. JK current is treated as negative for discharge and positive for charging or regeneration.

```text
Net consumption Ah = discharged Ah − recovered Ah
Efficiency km/Ah = valid distance km ÷ net consumption Ah
Estimated range km = remaining capacity Ah × efficiency km/Ah
```

A value such as `1Ah 3.0km` means roughly three kilometers per amp-hour consumed. Recovered charge reduces net consumption. To avoid unstable estimates just after starting, trip efficiency needs about 0.1 km and 0.01 Ah of net consumption. Range estimates use trip efficiency when available and fall back to the TOTAL average when it is not.

A ride starts after speed stays at or above 3 km/h for about three seconds. Current integration pauses immediately below 1 km/h. Fifteen seconds at low speed enters a temporary stop; resuming shortly afterward continues the same trip. About three minutes stationary ends the trip, whose results remain visible until the next confirmed start.

GPS recovery and Bluetooth reconnection establish new sampling baselines without filling in the missing interval. GPS needs two consecutive fresh fixes before energy integration resumes. Old, inaccurate, or implausible fixes are ignored. This may omit some distance during poor reception, but keeps stale current and speed out of the estimate.

Trip and session statistics, TOTAL, and the displayed remaining capacity are saved periodically. Restoring those results never restores old integration clocks. The app currently keeps the latest trip and cumulative totals rather than a browsable trip database.

Enable background energy collection in Settings to collect while using another app or locking the screen. A foreground service, persistent notification, and partial wake lock support Bluetooth and GPS collection; some phones also require allowing background activity in system settings. With the option off, energy collection pauses in the background. Collection cannot be guaranteed after the system forcibly stops the app. Reopening restores saved statistics and checks the connection.

Treat these estimates as a way to understand riding habits and range changes. Road conditions, temperature, wind, and riding style affect actual range; use the BMS, vehicle instruments, and measurements when assessing battery condition. This project is unaffiliated with JK. Its parser targets tested 300-byte JK status frames, and other hardware or firmware may need adaptation.

## Building from source

The project uses Kotlin, Jetpack Compose, and compile SDK 36. Use **JDK 17** and the included Gradle Wrapper. The current Gradle/Kotlin combination cannot run on JDK 25. Check your local SDK path after opening the project in Android Studio, or build from a terminal:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

On macOS or Linux, use `./gradlew`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. With a phone or emulator connected, `connectedDebugAndroidTest` checks navigation, restored energy data, automatic recording, and chart preferences.

For a signed release, set `JKBMS_KEYSTORE_PATH`, `JKBMS_KEYSTORE_PASSWORD`, `JKBMS_KEY_ALIAS`, and `JKBMS_KEY_PASSWORD` locally or through CI secrets, then run `assembleRelease`. Keys and passwords stay outside the project. Without signing configuration, the release APK is unsigned. Published signed builds are available in `app/release/app-release.apk` and GitHub Releases.

Start with [MainActivity.kt](app/src/main/java/com/yiming/jkbms/MainActivity.kt) for Bluetooth, GPS, and page coordination, and [EnergyTracker.kt](app/src/main/java/com/yiming/jkbms/model/EnergyTracker.kt) for trip detection and integration. [BleRecoveryPolicy.kt](app/src/main/java/com/yiming/jkbms/model/BleRecoveryPolicy.kt) handles timeout decisions, and [EnergyHistoryStore.kt](app/src/main/java/com/yiming/jkbms/model/EnergyHistoryStore.kt) saves previous energy statistics. Screens live in `ui/screens`; the ECharts bridge is in `ui/components/EChartsLine.kt`, with web assets in `app/src/main/assets/echarts`.

Please [open an issue](https://github.com/mazemingw/JKBMS-Android/issues) if something goes wrong. Your phone and Android version, BMS model, and the steps leading up to the problem make it easier to investigate.

## Open-source components

This project uses [Apache ECharts](https://echarts.apache.org/) 6.1.0 under Apache License 2.0, along with AndroidX, Jetpack Compose, and Material Components under their respective licenses. See [LICENSE](LICENSE) for the project license.

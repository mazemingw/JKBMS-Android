# Changelog

[简体中文](CHANGELOG.md) | [English](CHANGELOG_EN.md)

This file records major user-visible changes to JKBMS-Android.

## 1.3.0 (18) - 2026-11-18

### Fixes and Energy

- Fixed #1: fullscreen phones no longer enter compact mode accidentally. Compact information mode has a restore-navigation button and a system Back escape.
- Added a 15-second timeout for connection, service discovery, authentication, and initial battery data. Foreground recovery checks stalled sessions immediately.
- Only CRC-validated battery frames refresh the 3-second data timeout. Disconnects clear stale readings and break current, distance, and efficiency continuity.
- Persisted the previous trip, session statistics, and remaining capacity. The energy panel shows saved values immediately without restoring integration clocks.
- Kept dashboard values and units on one line, scaling text to fit narrow cards.

### Charts

- Recording starts automatically and includes GPS speed, which can be recorded independently while the BMS is offline.
- Added single/dual ECharts with independently selectable voltage, current, power, SOC, or speed sources. Choices persist across restarts.
- Snapshot playback continues live recording. Resuming preserves samples; invalid speed and disconnections create gaps.

### Distribution

- Switched to a new release key: **1.3.0 cannot update 1.2.8 or older debug builds in place**. Back up needed information before uninstalling; uninstalling clears local data.
- Provided a signed APK verified with APK Signature Scheme v2. Release signing uses environment variables; keys and passwords remain outside the repository.


## 1.2.8 (17) - 2026-09-07

### TRIP A and TOTAL

- Expanded the energy card to three modes—Chart, TRIP A, and TOTAL—with a single cycle button in the upper-right corner.
- TRIP A shows current distance, net consumption, trip efficiency, recovered charge, and estimated range.
- Added persistent TOTAL distance, net consumption, cumulative recovery, and long-term average efficiency across app launches.
- Estimated range falls back to TOTAL average efficiency when TRIP A does not yet have enough data.
- TOTAL accumulates only while GPS and BMS data are both valid and excludes MOCK data.

### Settings

- Added an energy-panel visibility switch.
- Added a confirmation-protected `Clear TOTAL` action that preserves the current TRIP A.
- Added a home MOCK-button visibility switch. Hiding the entry point also stops an active MOCK session.

### Interface

- Reworked bottom navigation as a floating oval capsule with a translucent glass gradient, highlight border, shadow, and smooth selection animation.
- Updated the launcher and adaptive-icon resources.

### Distribution

- Added an officially signed APK verified with APK Signature Scheme v2 and attached it to the GitHub Release.
- Added a complete English README and English changelog.

## 1.2.6 (15) - 2026-09-07

### Background Collection and BLE Recovery

- Added `Background energy collection` in Settings, backed by a foreground service, persistent notification, and partial wake lock.
- Fixed app lifecycle handling that previously stopped GPS and energy integration after entering the background.
- Returning to the foreground now restarts BLE polling, RSSI updates, and the data watchdog; stale data directly reconnects to the last successful device.
- Fixed incomplete GATT cleanup after disconnect and late callbacks from old GATT instances overwriting the new connection state.
- BLE and location callbacks now update UI state on the main thread to reduce concurrent state races.

### GPS and Energy Continuity

- After GPS recovery, two consecutive fresh fixes are required. The first fix rebuilds the baseline without immediately resuming integration.
- Rejects cached fixes older than 2.5 seconds and speeds above 200 km/h to avoid efficiency spikes after positioning resumes.
- Invalid GPS immediately breaks current integration and the rolling one-minute window, while advancing pause/trip-end timing as a no-speed state.
- Added regression tests for recovery after extended GPS loss and pausing while location is unavailable.

## 1.2.5 (14) - 2026-08-30

### Riding Efficiency

- Added combined integration of GPS speed and BMS current.
- Displays efficiency as `1Ah Xkm` and subtracts positive-current regenerative braking from net consumption.
- Added current net consumption, average efficiency, estimated range, recovered charge, and trip aggregates.
- Added a rolling one-minute efficiency line with MAX/MIN marks and a trip-wide discharge/recovery pie chart.
- Energy integration freezes immediately below the GPS stop threshold and does not backfill across the stopped interval.
- Added GPS accuracy filtering, location-timeout restart, and data-continuity protection.

### Charts and Interface

- Retained Compose Canvas for the main power gauge and moved other live line charts to bundled ECharts.
- ECharts uses fixed slots, stable point IDs, and linear update animation for smoother scrolling.
- Improved the one-minute window, axis density, extrema labels, pie layout, and card height.
- Fixed the main gauge smoke animation stopping, moving at the wrong speed, and visibly repeating.
- Current and power gauges support bidirectional display centered on zero.

### BLE and MOCK

- Increased the target BMS status polling rate from 3 seconds to 500 ms.
- Added request-in-progress and 1.5-second timeout protection to prevent overlapping fragmented BLE responses.
- Charts sample only validated BMS frames and cap recordings to prevent unbounded growth.
- Fixed UI flicker caused by enabling MOCK while connected to a real BMS.
- MOCK uses the same energy-calculation path as real data and covers regeneration and stopped states.

### Verification

- Added unit tests for constant-speed integration, stopping, continuity after resuming, regeneration, and the rolling one-minute trend window.
- Installed and smoke-tested the debug APK on a physical Android device.

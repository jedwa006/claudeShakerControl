# Claude Code Project Instructions

## Environment Setup

### Java/Gradle
This project requires Java 17+ for Hilt/Dagger. Use the JDK bundled with Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Always use this JAVA_HOME when running Gradle commands:
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew <task>
```

Or with full environment:
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH:$HOME/Library/Android/sdk/platform-tools"
./gradlew <task>
```

### Android SDK
```bash
export ANDROID_HOME=~/Library/Android/sdk
export PATH="$PATH:$ANDROID_HOME/platform-tools"
```

### Common Commands
```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run lint
./gradlew lintDebug

# Run unit tests
./gradlew testDebugUnitTest

# Check connected devices
adb devices

# View BLE logs
adb logcat -s BleManager:D BleMachineRepository:D SessionManager:D

# Take screenshot
adb exec-out screencap -p > screenshot.png
```

## Project Structure
- Android app: Kotlin + Jetpack Compose + Hilt
- BLE communication with ESP32-S3 firmware
- Wire protocol: CRC-16/CCITT-FALSE framed messages

## UI Test Coordinates (Lenovo TB351FU, landscape 2000x1200)

Use `adb shell uiautomator dump` to get fresh coordinates if layout changes.

### Home Screen
- Hamburger menu: tap 60 107
- "Disconnected" status: tap 862 108 → navigates to Devices
- "Connected" status: tap 862 108 → navigates to Devices

### Devices Screen
- Reconnect button: tap 1863 242
- Disconnect button: tap 1303 168 (same location as Reconnect)
- Scan button: tap 1325 271
- Forget button: tap 1195 168

### Navigation Drawer
- Open: tap hamburger menu at 60 107, or swipe from x=0 to x=300 at y=400
- Home: tap 270 219
- Run: tap 270 303
- Devices: tap 270 387
- Alarms: tap 270 471
- I/O Control: tap 270 555
- Diagnostics: tap 270 639
- Settings: tap 270 723
- Service mode: tap 270 833

## Related Repositories
- Firmware: `/tmp/NuNuCryoShaker` (ESP32-S3 BLE GATT server)
- Handoff doc: `docs/FIRMWARE_AGENT_PROMPT.md`

## Key Documentation
- `docs/AGENT_LOG.md` - Development history and decisions
- `docs/MCU_docs/95-implementation-checklist.md` - Acceptance criteria
- `docs/MCU_docs/90-command-catalog.md` - Protocol reference
- `docs/MCU_docs/30-wire-protocol.md` - Frame format
- `docs/MCU_docs/80-gatt-uuids.md` - BLE UUIDs

## Current Project Status

### Completed Stages
- **Stage 0**: Documentation and planning
- **Stage 1**: UI skeleton with all screens
- **Stage 2**: BLE foundation (scan, connect, wire protocol)
- **Stage 3**: Run workflow with timer countdown
- **Stage 4**: PID detail pages with setpoint/mode control
- **Stage 5**: Capability bits and alarm acknowledgment
- **Stage 6**: Auto-reconnection and device persistence
- **Stage 7**: Session health and signal quality indicators

### Current Status
- Real device testing complete with ESP32-S3 MCU
- OPEN_SESSION, KEEPALIVE, telemetry all working
- 101 unit tests passing
- I/O Control screen implemented with DI/RO visualization
- RS-485 PID controller integration prepared (awaiting firmware)
- Firmware v0.2.0+26011901 verified on device

### Stage 8 Progress (In Progress)
**Completed:**
- I/O Control screen with 8 digital inputs and 8 relay outputs
- Simulation mode for testing DI in service mode
- Relay control commands (requires service mode)
- 14 new unit tests for IoViewModel
- Navigation architecture: top-level pages (drawer) don't show back button
- Connect button on Home now navigates to Devices when disconnected
- Back button visual shift fixed (48dp fixed box with crossfade)
- **Firmware version display**: Full version with build ID (e.g., "0.2.0+26011901")
- **PID controller status in Diagnostics**: Shows RS-485 controller connection status (Online/Stale/Offline)
- **Capability table in Diagnostics**: Color-coded chips (Required/Optional/Not installed)
- **Dynamic PID tiles**: Run screen shows only connected controllers with count badge
- **AL1/AL2 alarm relay support**: PidData model and UI indicators ready for alarm states
- **IoScreen UI improvements**: Pulsing green indicators for active relays, clearer button labels

**Remaining:**
1. Recipe persistence (save/load recipes)
2. Recipe transfer to MCU (if applicable)
3. QR code recipe import (future feature)

### Known Issues
- Connect popup on first start doesn't initiate connect sequence (must use Devices screen)
- Relay commands show "NO ARGS" error (firmware needs SET_RELAY handler - see FIRMWARE_AGENT_PROMPT.md)

### RS-485 Integration Status
The app is ready to receive RS-485 PID controller data from firmware:
- **Telemetry parsing**: Expects `controller_count` and per-controller blocks
- **AL1/AL2 bits**: Ready to display alarm relay states when firmware provides them
- **Dynamic display**: Only shows controllers with capability > NOT_PRESENT
- **Start gating**: Correctly blocks start when Required controllers are offline

Currently testing with PID 3 (LN2 line) - awaiting firmware for PID 1 (Axle) and PID 2 (Orbital).

### Implementation Checklist Progress
From `docs/MCU_docs/95-implementation-checklist.md`:

**Protocol & Contract (Section A)**: ✅ Complete
- UUIDs pinned in code and docs
- Frame parser with CRC validation
- Round-trip tested with real MCU

**ESP Firmware (Section B)**: ✅ Implemented by firmware agent
- Advertising, GATT server, notifications
- Command handling for OPEN_SESSION, KEEPALIVE
- Session/lease policy

**Android App (Section C)**: ✅ Complete
- [x] C1: Permissions handling
- [x] C2: Scan and filter by name prefix
- [x] C3: Connect and discover services
- [x] C4: Subscribe to telemetry and events
- [x] C5: OPEN_SESSION + KEEPALIVE loop
- [x] C6: Command queue with ACK correlation
- [x] C7: Reconnect state machine

**Performance (Section D)**: ✅ Verified
- 10Hz telemetry stable
- Command responses < 200ms

**Safety (Section E)**: Partial
- [ ] E1: Start gating (needs MCU policy enforcement)
- [ ] E2: Mid-run disconnect (needs run implementation)
- [ ] E3: E-stop (needs hardware)

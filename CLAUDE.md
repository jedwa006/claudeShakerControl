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

# Run unit tests (101 tests)
./gradlew testDebugUnitTest

# Run instrumented UI tests (11 tests, requires connected device)
./gradlew connectedDebugAndroidTest

# Check connected devices
adb devices

# View BLE logs
adb logcat -s BleManager:D BleMachineRepository:D SessionManager:D

# Take screenshot
adb exec-out screencap -p > screenshot.png
```

## Testing

### Test Summary
| Type | Count | Location | Command |
|------|-------|----------|---------|
| Unit tests | 101 | `app/src/test/` | `./gradlew testDebugUnitTest` |
| UI tests | 11 | `app/src/androidTest/` | `./gradlew connectedDebugAndroidTest` |
| Screenshot tests | Manual | `scripts/screenshot-test.sh` | See below |

### Unit Tests
Unit tests run on the JVM without a device. They test ViewModels, parsers, and repository logic using `MockMachineRepository`.

```bash
./gradlew testDebugUnitTest
```

Key test files:
- `WireProtocolTest.kt` - Frame encoding/decoding, CRC validation
- `ParserTest.kt` - Telemetry parsing
- `IoViewModelTest.kt` - I/O control logic
- `PidDetailViewModelTest.kt` - PID screen logic
- `AlarmsViewModelTest.kt` - Alarm management

### Instrumented UI Tests
UI tests run on a connected device/emulator. They verify navigation and screen behavior using Compose Testing.

```bash
./gradlew connectedDebugAndroidTest
```

Test file: `app/src/androidTest/kotlin/com/shakercontrol/app/ui/NavigationTest.kt`

Tests include:
- Home screen verification
- Drawer navigation (Run, Settings, I/O, Diagnostics)
- Back stack behavior
- Service mode toggle and UI changes

The tests use `MockMachineRepository` via Hilt's `@TestInstallIn` so they run without BLE hardware.

### Visual Regression Testing (Screenshots)
Manual screenshot testing for visual regression detection.

```bash
# Capture current screenshots
./scripts/screenshot-test.sh capture

# Capture reference screenshots (baseline)
./scripts/screenshot-test.sh reference

# Compare current vs reference (requires ImageMagick)
./scripts/screenshot-test.sh compare

# Update reference from current
./scripts/screenshot-test.sh update
```

Screenshots are stored in:
- `screenshots/reference/` - Baseline images
- `screenshots/current/` - Current test images
- `screenshots/diff/` - Diff images (when compare finds differences)

### When to Run Tests
- **Before commits**: Run unit tests (`./gradlew testDebugUnitTest`)
- **Before PRs**: Run full test suite (see Pre-PR Checklist below)
- **After UI changes**: Run screenshot comparison
- **CI/CD**: Unit tests always, UI tests on feature branches

### Pre-PR Checklist
Before creating a PR, run this sequence:
```bash
# 1. Unit tests (required)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest

# 2. Build and install
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug

# 3. UI tests (requires connected device)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew connectedDebugAndroidTest

# 4. Screenshot comparison (after UI changes)
./scripts/screenshot-test.sh capture
./scripts/screenshot-test.sh compare
# If intentional changes: ./scripts/screenshot-test.sh update

# 5. Reinstall app for manual testing (UI tests uninstall the app)
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug
```

**Claude workflow**: Before major PRs, automatically run steps 1-5 above. If screenshot comparison shows differences, review the diff images in `screenshots/diff/` and update reference if changes are intentional. Always reinstall after UI tests so the user can manually verify changes.

### Deep Links (Debug Navigation)
The app supports deep links for programmatic navigation and actions. Use these via adb:

```bash
# Navigation deep links (navigate to screens)
adb shell am start -a android.intent.action.VIEW -d "shaker://run" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://settings" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://devices" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://diagnostics" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://alarms" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://io" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://pid/1" com.shakercontrol.app.debug

# Action deep links (trigger behavior)
adb shell am start -a android.intent.action.VIEW -d "shaker://action/reconnect" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://action/connect" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://action/disconnect" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://action/service-mode/enable" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://action/service-mode/disable" com.shakercontrol.app.debug
adb shell am start -a android.intent.action.VIEW -d "shaker://action/service-mode/toggle" com.shakercontrol.app.debug
```

**Important:** On first app boot, always use `shaker://action/reconnect` first to connect to the controller, then navigate to screens. Navigation deep links only work reliably on cold start; when app is already running, they deliver intent but may not navigate.

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

### Devices Screen (accessed via Settings > Scan)
- Reconnect button: tap 1863 242
- Disconnect button: tap 1303 168 (same location as Reconnect)
- Scan button: tap 1325 271
- Forget button: tap 1195 168

### Navigation Drawer
- Open: tap hamburger menu at 60 107, or swipe from x=0 to x=300 at y=400
- Home: tap 270 219 (only visible when disconnected - serves as landing/status screen)
- Run: tap 270 303
- Alarms: tap 270 387
- I/O Control: tap 270 471 (only visible in service mode)
- Diagnostics: tap 270 555
- Settings: tap 270 639

**Removed from drawer:**
- **Devices**: Now accessed via Settings > Scan button, or by clicking "Disconnected" status chip
- **Service mode toggle**: Enable via Settings screen or deep link (`shaker://action/service-mode/enable`)

### Settings Screen Device Management
The Settings screen now includes a Device section with:
- Connection status (Connected/Connecting/Disconnected)
- Connected device name or last known device
- Auto-reconnect toggle
- Action buttons: Disconnect (when connected), Reconnect + Forget + Scan (when disconnected)

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
- **Capability table in Diagnostics**: Color-coded chips (Required/Optional/Not installed), editable in service mode
- **Dynamic PID tiles**: Run screen shows only connected controllers with count badge
- **AL1/AL2 alarm relay support**: PidData model and UI indicators ready for alarm states
- **IoScreen UI improvements**: Pulsing green indicators for active relays, clearer button labels
- **PID error handling**: Probe error detection (HHHH/LLLL), pulsing red border for errors, status badges
- **Capability override editing**: Service mode allows editing capability levels with OVERRIDE badge
- **Threshold tuning**: Stale threshold 1500ms (was 500ms), probe error threshold 500°C (was 3000°C)

**Remaining:**
1. Recipe persistence (save/load recipes)
2. Recipe transfer to MCU (if applicable)
3. QR code recipe import (future feature)

### Known Issues
- Connect popup on first start doesn't initiate connect sequence (must use Devices screen)
- Relay commands show "NO ARGS" error (firmware needs SET_RELAY handler - see FIRMWARE_AGENT_PROMPT.md)

### RS-485 Integration Status
The app is now receiving real RS-485 PID controller data from firmware:
- **PID ID mapping**: ID 1=LN2 Cold (Optional), ID 2=Axle (Required), ID 3=Orbital (Required)
- **Telemetry parsing**: 3 controllers with PV/SV/OP/mode/status at ~10Hz
- **AL1/AL2 bits**: Ready to display alarm relay states when firmware provides them
- **Probe error detection**: Detects HHHH (over-range) and LLLL (under-range) from E5CC controllers
- **Dynamic display**: Only shows controllers with capability > NOT_PRESENT
- **Start gating**: Correctly blocks start when Required controllers are offline

Currently testing with all 3 PID controllers connected via RS-485. PID 1 (LN2) has disconnected probe showing HHHH error.

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

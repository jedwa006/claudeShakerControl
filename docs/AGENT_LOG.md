# Agent Log — Cryogenic Shaker Mill HMI

This document tracks decisions, assumptions, and changes across development stages.

---

## Docs Inventory

| Document | Location | Governs |
|----------|----------|---------|
| Dashboard Spec v1 | `docs/dashboard-sec-v1.md` | UI structure, screens, components, capability bit behavior |
| UI Copy & Labels v1 | `docs/ui-copy-labels-v1.md` | All UI text, tone rules, error patterns |
| GATT Schema | `docs/MCU_docs/20-gatt-schema.md` | BLE service/characteristic structure |
| GATT UUIDs | `docs/MCU_docs/80-gatt-uuids.md` | Pinned UUIDs (do not change) |
| Wire Protocol | `docs/MCU_docs/30-wire-protocol.md` | Frame format, message types |
| Command Catalog | `docs/MCU_docs/90-command-catalog.md` | Full protocol reference, worked examples |
| Safety & Heartbeat | `docs/MCU_docs/40-safety-heartbeat-and-policies.md` | Session/lease model, start gating |
| State Machines | `docs/MCU_docs/96-state-machine.md` | App and ESP state enums, transitions |
| MVP Scope v0 | `docs/MCU_docs/99-mvp-scope-v0.md` | Feature boundaries, done definition |
| Hardware Overview | `docs/MCU_docs/05-hardware-overview.md` | System hardware summary |
| Controller HW | `docs/MCU_docs/07-hardware-controller-waveshare-esp32-s3-eth-8di-8ro.md` | Waveshare board specs, pin mappings |
| Kotlin Architecture | `docs/MCU_docs/50-app-architecture-kotlin.md` | Recommended app module layout |
| System Inventory | `docs/system-inventory.md` | Sensors, peripherals, capability bits |

---

## Stage 0 — Alignment + Plan

**Date:** 2026-01-15
**Branch:** `chore/stage-0-docs`
**Status:** Complete
**Merged to dev:** 2026-01-15 (commit 718ec00)

### Scope Statement
Establish project documentation scaffolding, framework decision, and implementation plan. No application code in this stage.

### Decisions Made

#### 1. Framework Selection: Kotlin + Jetpack Compose
**Rationale:**
- Direct access to Android BLE APIs without plugin intermediary
- Native performance for 10Hz telemetry rendering
- Official Google toolkit with strong tablet/landscape support
- MVVM + Hilt ecosystem for testable architecture
- No cross-platform requirement (Android-only target)

**Alternatives considered:**
- Flutter: BLE plugin dependency risk; Dart runtime overhead; cross-platform benefit unused
- Avalonia/.NET MAUI: Smaller Android ecosystem; team skill overhead
- React Native: JavaScript bridge overhead; not ideal for real-time HMI

#### 2. Run Timing Semantics
- **Hold timing:** `HOLD × (cycles - 1)` — no hold after final milling phase
- **Pause behavior:** Current phase timer freezes; resumes from exact point
- **Recipe storage:** MCU stores recipes on SD card; app can construct/send recipes

#### 3. Capability Bits Architecture
- Sync once on first connection
- MCU is authoritative for enforcement
- App references static list for UI gating
- Reboot/config save can push updated capabilities

#### 4. Service Mode
- Warning-acknowledged entry (2-step: checkbox + confirm)
- Does NOT persist across app restarts
- Superuser button (PIN-protected, dummy PIN for now) enables service mode on MCU restart while tablet stays connected
- Superuser feature may be debug-only or locked out in production

#### 5. Command Gating Strategy
- App sends commands optimistically
- MCU rejects if invalid
- App displays rejection reason from ACK

#### 6. ESP-IDF Version
- Current release v5.5.2 is sufficient
- Will note if any feature requires v6.0

### Clarifications Received

| Topic | Resolution |
|-------|------------|
| Hold timing | `HOLD × (cycles - 1)` |
| Pause behavior | Timer freezes, resumes from same point |
| Recipe storage | SD card on MCU; future QR code import |
| Capability bits | PID 1/2/3, LN2 valve, door actuator, door switch, relay banks (internal + external RS485) |
| Service mode | Warning-acknowledged, no persist, superuser button |
| Signing | Debug-only for now; will set up keystore later |
| Kiosk mode | Not needed for development; future consideration |

### Files Created This Stage
- `AGENTS.md` — Operational guide for agents
- `docs/AGENT_LOG.md` — This file
- `CHANGELOG.md` — User-facing changes
- `docs/system-inventory.md` — Sensors, peripherals, capability bits

### How to Test
Stage 0 is documentation only. Verify:
1. All listed files exist and are readable
2. Git branch structure: `main` → `dev` → `chore/stage-0-docs`
3. No application code changes

### Next Steps (Stage 1)
1. Initialize Android project (Kotlin, Compose, Material 3, Hilt)
2. Implement dark theme with color semantics
3. Create navigation scaffold
4. Build Home and Run screen skeletons with mock data

---

## Stage 1 — Bootstrap + UI Skeleton

**Date:** 2026-01-16
**Branch:** `feature/stage-1-ui-skeleton`
**Status:** Complete
**Merged to dev:** 2026-01-16 (commit 768d9d8)

### Scope Statement
Create the complete Android project scaffolding with Kotlin/Compose/Hilt and implement all UI screens as skeletons with mock data.

### Architecture Implemented

```
app/src/main/kotlin/com/shakercontrol/app/
├── MainActivity.kt           # Entry point
├── ShakerControlApp.kt       # Hilt Application class
├── data/repository/
│   ├── MachineRepository.kt  # Interface
│   └── MockMachineRepository.kt  # Mock implementation
├── di/
│   └── AppModule.kt          # Hilt DI module
├── domain/model/
│   ├── Alarm.kt              # Alarm with severity, source, state
│   ├── ConnectionState.kt    # BLE states (DISCONNECTED → LIVE)
│   ├── MachineState.kt       # IDLE, READY, RUNNING, PAUSED, FAULT, E_STOP
│   ├── PidData.kt            # PID with PV/SV/capability level
│   ├── Recipe.kt             # Interval cycle with hold timing
│   └── SystemStatus.kt       # Combined status, IO, interlocks
└── ui/
    ├── MainViewModel.kt
    ├── ShakerControlApp.kt   # Main composable with drawer nav
    ├── alarms/               # Alarms list screen
    ├── components/           # StatusStrip, LedIndicator, ServiceModeBanner
    ├── devices/              # BLE scan/connect placeholder
    ├── diagnostics/          # Connection, heartbeats, firmware info
    ├── home/                 # Dashboard with cards
    ├── navigation/           # NavRoutes, AppNavHost
    ├── pid/                  # PID detail pages
    ├── run/                  # Cockpit with recipe editor
    ├── settings/             # Theme, time format, about
    └── theme/                # Color, Type, Theme (always dark)
```

### Key Implementation Details

#### Theme
- Material 3 dark theme (always dark for HMI readability)
- Semantic colors: Normal (green), Active (green), Warning (amber), Alarm (red), Stale (gray)
- Tablet-optimized typography with adequate tap targets

#### Components
- **StatusStrip**: Global header showing connection status (green/yellow/red chip), MCU heartbeat age, machine state, alarm count
- **LedIndicator**: LED with pulsing animation for active outputs, stale dimming for old data
- **ServiceModeBanner**: Warning banner when service mode is active

#### Screens
| Screen | Purpose | Features |
|--------|---------|----------|
| Home | Dashboard | Run card, Temperatures card (3 PIDs), Status card, Diagnostics link, Settings link |
| Run | Cockpit | Recipe editor (milling/hold/cycles), Start/Pause/Stop controls, PID tiles, Indicators |
| Devices | BLE | Scan button, paired devices list (placeholder) |
| PID Detail | Per-PID | PV/SV display, tuning inputs, output meter (placeholder) |
| Alarms | List | Active/history toggle, alarm cards with severity, clear button |
| Diagnostics | Debug | Connection state, heartbeat ages, firmware version, capabilities |
| Settings | Config | Theme toggle, time format, export logs, about section |

#### Navigation
- Modal navigation drawer with persistent StatusStrip
- Routes: Home, Run, Devices, PidDetail/{id}, Alarms, Diagnostics, Settings
- Service mode toggle in drawer with warning color

### Build Configuration
- Gradle 8.9 with version catalog
- AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12.01
- Hilt 2.53.1 for dependency injection
- minSdk 31 (Android 12), targetSdk 35 (Android 16)
- BLE permissions: BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT

### Files Created This Stage
- Complete Android project (53 files, 5146 lines)
- `.gitignore` with standard Android exclusions
- Gradle wrapper and build configuration
- All UI screens and components
- Mock data repository

### How to Test
1. `./gradlew assembleDebug` — Build APK
2. `./gradlew lintDebug` — Run lint checks
3. Install on emulator: `./gradlew installDebug`
4. Verify: Dark theme, all screens accessible via drawer, mock data displayed

### Screenshot Verification
Tested on Lenovo_Tab_Plus_11.5 emulator (2000×1200, Android 16):
- Home screen: Cards layout, temperature display, status LEDs
- Run screen: Recipe editor, controls, PID tiles, indicators

### Next Steps (Stage 2)
1. Implement BleManager with scan/connect/disconnect
2. Create BleMachineRepository implementing MachineRepository interface
3. Wire up GATT UUIDs and characteristic parsing
4. Implement session/lease heartbeat (OPEN_SESSION, KEEPALIVE at 1Hz)

---

## Stage 2 — BLE Foundation

**Date:** 2026-01-16
**Branch:** `feature/stage-2-ble-foundation`
**Status:** Complete
**Merged to dev:** 2026-01-16 (commit f1e99b2)

### Scope Statement
Implement complete BLE communication infrastructure: GATT UUIDs, wire protocol codec, BleManager for device operations, and BleMachineRepository implementing the MachineRepository interface with real BLE backend.

### Architecture Implemented

```
app/src/main/kotlin/com/shakercontrol/app/
├── data/
│   ├── ble/
│   │   ├── BleConstants.kt      # GATT UUIDs, message types, command IDs, enums
│   │   ├── BleManager.kt        # BLE scan/connect/disconnect, GATT operations
│   │   └── WireProtocol.kt      # Frame encoder/decoder, CRC-16, parsers
│   └── repository/
│       ├── BleMachineRepository.kt  # Real BLE implementation
│       └── MockMachineRepository.kt # Mock (retained for testing)
└── ui/devices/
    ├── DevicesScreen.kt         # Real scan/connect UI
    └── DevicesViewModel.kt      # ViewModel for BLE operations
```

### Key Implementation Details

#### BleConstants
- **Service UUID:** `F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E60`
- **Characteristics:**
  - Device Info: `...5E61` (read)
  - Telemetry Stream: `...5E62` (notify, 10Hz)
  - Command RX: `...5E63` (write)
  - Events/ACKs: `...5E64` (notify)
- **Enums:** MessageType, CommandId, AckStatus, EventSeverity, RunMode, StopMode, RelayState, ControllerMode

#### WireProtocol
- Frame layout: `proto_ver(1) | msg_type(1) | seq(2) | payload_len(2) | payload(N) | crc16(2)`
- Little-endian byte order throughout
- CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF, no reflection)
- Parsers: TelemetryParser, CommandAckParser, EventParser
- Builders: CommandPayloadBuilder for all command payloads

#### BleManager
- Singleton injected via Hilt
- Scan with coroutine-based timeout (10s default)
- Connect/disconnect with GATT callback handling
- Notification setup for telemetry and events characteristics
- Command sending with sequence number tracking
- Flows: `telemetryFlow`, `eventFlow`, `ackFlow` for reactive updates
- States: `DISCONNECTED`, `SCANNING`, `CONNECTING`, `CONNECTED`, `READY`

#### BleMachineRepository
- Implements full MachineRepository interface
- Session management:
  - `openSession()` sends OPEN_SESSION with client nonce
  - Heartbeat coroutine sends KEEPALIVE every 1000ms
  - Session ID tracking for authenticated commands
- Telemetry mapping:
  - PV/SV scaling: x10 (e.g., 123.4°C → 1234 in protocol)
  - DI/RO bit unpacking to IoStatus
  - Alarm bits to Alarm list
- Command methods: startRun, stopRun, pauseRun, resumeRun, setRelay, setSv, setMode

#### DevicesScreen
- Real scan button with loading indicator
- Device list showing name, address, RSSI with signal strength icon
- Connect/disconnect per device
- Connection state display (Disconnected/Scanning/Connecting/Connected/Ready)
- Empty state when no devices found

### Protocol Compliance
Implementation follows `docs/MCU_docs/` specifications exactly:
- GATT UUIDs from `80-gatt-uuids.md`
- Frame format from `30-wire-protocol.md`
- Command payloads from `90-command-catalog.md`
- Session/lease model from `40-safety-heartbeat-and-policies.md`

### Files Created This Stage
- `data/ble/BleConstants.kt` — 209 lines
- `data/ble/BleManager.kt` — 509 lines
- `data/ble/WireProtocol.kt` — 425 lines
- `data/repository/BleMachineRepository.kt` — 531 lines
- `ui/devices/DevicesViewModel.kt` — 65 lines
- Modified: `di/AppModule.kt`, `ui/devices/DevicesScreen.kt`
- Total: ~2060 lines added

### How to Test
1. `./gradlew assembleDebug` — Build APK
2. `./gradlew lintDebug` — Run lint checks
3. Install on physical device with BLE
4. Navigate to Devices screen, tap Scan
5. (Requires ESP32 MCU running firmware to complete connection)

### Known Limitations
- BLE operations require physical device (emulator has limited BLE support)
- Session lease timeout (3000ms) not yet surfaced in UI
- No reconnection logic on unexpected disconnect (future stage)

### Next Steps (Stage 3)
1. Wire up Run screen to real commands (START_RUN, STOP_RUN, PAUSE_RUN, RESUME_RUN)
2. Implement recipe transfer to MCU
3. Display run progress from telemetry (phase, elapsed time)
4. Handle ACK failures with user feedback

---

## Stage 3 — Run Workflow Functionality

**Date:** 2026-01-16
**Branch:** `feature/stage-3-run-workflow`
**Status:** Complete
**Merged to dev:** 2026-01-16 (commit b15a679)

### Scope Statement
Implement live run progress countdown timer and command feedback UI. Fix protocol issues with pause command.

### Features Implemented

#### Timer Countdown
- Run timer job updates progress every second during execution
- Correctly tracks elapsed time across pause/resume cycles
- Calculates current phase (milling/holding) and cycle number from elapsed time
- Displays phase remaining and total remaining countdown in real-time
- Automatically completes run when total time elapses
- Timer freezes when paused, resumes from exact point

#### Command Feedback
- `RunUiEvent` sealed class for error/success events
- `RunViewModel` exposes `uiEvents` flow for snackbar messages
- `isExecutingCommand` state disables buttons while commands are in flight
- `ControlsSection` shows loading spinner during command execution
- Error snackbar on command failures with user-friendly messages

#### Protocol Fixes
- Added `pauseRun()` payload builder in `WireProtocol`
- Fixed `pauseRun()` to use correct command ID `PAUSE_RUN` (0x0104)
- Previously was incorrectly using `STOP_RUN` with `NORMAL_STOP`
- Added `resumeRun()` convenience builder (uses `startRun` with NORMAL mode)

### Files Modified
- `data/ble/WireProtocol.kt` — Added pauseRun/resumeRun payload builders
- `data/repository/BleMachineRepository.kt` — Run timer logic, pause accumulator
- `ui/run/RunViewModel.kt` — UI events, command execution state
- `ui/run/RunScreen.kt` — Snackbar host, event collection
- `ui/run/RunSections.kt` — Loading indicator, disabled state handling

### How to Test
1. Build and install: `./gradlew installDebug`
2. Navigate to Run screen
3. Start a run (with mock data or connected MCU)
4. Observe timer counting down in real-time
5. Pause — timer should freeze
6. Resume — timer should continue from where it was
7. Stop — timer should reset
8. (With no MCU) Start should fail and show snackbar error

### Known Limitations
- Recipe is not transferred to MCU (app-side timer only for now)
- MCU should report run progress in telemetry for sync (future stage)
- No recipe persistence (in-memory only)

### Next Steps (Stage 4)
1. Implement PID detail pages with real data
2. Add SV editing and mode changes via BLE commands
3. Display PID tuning parameters (if capability bit set)

---

## Stage 4 — PID Summaries + Pages

**Date:** 2026-01-16
**Branch:** `feature/stage-4-pid-pages`
**Status:** Complete
**Merged to dev:** 2026-01-16

### Scope Statement
Implement functional PID detail pages with real setpoint editing and mode control via BLE commands.

### Features Implemented

#### Setpoint Control
- Text input field with current setpoint value
- Apply button to send new setpoint to MCU
- Keyboard "Done" action also applies the value
- Shows current setpoint below input for reference
- Input disabled when not connected or command executing

#### Mode Control
- Segmented button row for Stop/Manual/Auto modes
- Color-coded selection (red for Stop, teal for Manual, blue for Auto)
- Mode description text explains current mode behavior
- Disabled when not connected or command executing

#### Command Feedback
- `PidUiEvent` sealed class for error/success events
- `isExecutingCommand` state disables controls while in flight
- Loading spinner in card headers during command execution
- Error snackbar on command failures

#### Repository Interface
- Added `setSetpoint(controllerId, setpoint): Result<Unit>`
- Added `setMode(controllerId, mode): Result<Unit>`
- Implemented in both `BleMachineRepository` and `MockMachineRepository`

### Protocol Details
- `SET_SV` command (0x0020): Setpoint scaled x10 (e.g., 30.0°C → 300)
- `SET_MODE` command (0x0021): Mode codes 0=Stop, 1=Manual, 2=Auto, 3=Program
- Both commands include session ID validation
- Optimistic UI update on successful ACK

### Files Modified
- `data/repository/MachineRepository.kt` — Added setSetpoint/setMode interface
- `data/repository/BleMachineRepository.kt` — BLE command implementation
- `data/repository/MockMachineRepository.kt` — Mock implementation with delay
- `ui/pid/PidDetailViewModel.kt` — Command handling with UI events
- `ui/pid/PidDetailScreen.kt` — Full setpoint/mode control UI

### How to Test
1. Build and install: `./gradlew installDebug`
2. Navigate to Home → tap any PID row (Axle/Orbital/LN2)
3. (Mock mode) Controls are enabled when connection state is LIVE
4. Change setpoint: type new value, tap Apply
5. Change mode: tap Stop/Manual/Auto buttons
6. Observe optimistic UI updates
7. (With MCU) Verify commands are sent and ACKs received

### Next Steps (Stage 5)
1. Implement capability bit gating for UI controls
2. Add alarm list screen with acknowledge functionality
3. Implement alarm banner and notification system

---

## Stage 5 — Capability-Bit Gating + Alarms

**Date:** 2026-01-16
**Branch:** `feature/stage-5-capabilities-alarms`
**Status:** Complete

### Scope Statement
Implement subsystem capability model for UI gating and enable alarm acknowledgment functionality.

### Features Implemented

#### Subsystem Capabilities Model
- `CapabilityLevel` enum: `REQUIRED`, `OPTIONAL`, `NOT_PRESENT`
- `SubsystemCapabilities` data class with 8 subsystem bits:
  - PID 1/2/3 controllers
  - LN2 valve, door actuator, door switch
  - Internal relay bank, external RS485 relay bank
- `SubsystemStatus` enum for runtime status: `OK`, `OFFLINE`, `FAULT`
- `StartGatingResult` for blocking start operations with reason
- Capability parsing from MCU bits via `fromBits()`

#### Start Gating Logic
- `RunViewModel.startGating` flow combines connection state, machine state, and PID capability status
- Start button disabled with reason tooltip when:
  - Not connected
  - Machine not in startable state (READY/PAUSED)
  - Required subsystems offline or faulted
- `ControlsSection` accepts `StartGatingResult` parameter for UI feedback

#### Alarm Acknowledgment
- Added `acknowledgeAlarm(alarmId)` to repository interface
- Added `clearLatchedAlarms()` to repository interface
- Implemented in both `BleMachineRepository` (BLE command 0x0030) and `MockMachineRepository`
- `AlarmsViewModel` with:
  - `isConnected` and `isExecutingCommand` states
  - `AlarmsUiEvent` sealed class for command feedback
  - Methods wire through to repository commands

#### Alarms Screen Updates
- Acknowledge button enabled when connected and alarm is active/unacknowledged
- Clear Latched button appears when latched alarms exist
- Snackbar host for error feedback
- UI events collection for command success/failure

### Protocol Details
- `ACK_ALARM` command (0x0030): Sends alarm ID to acknowledge
- `CLEAR_LATCHED` command (0x0031): Clears all acknowledged/cleared alarms
- Both commands include session ID validation

### Files Created
- `domain/model/SubsystemCapabilities.kt` — 130 lines (capability model, gating result)

### Files Modified
- `domain/model/SystemStatus.kt` — Added `capabilities` field with default
- `data/repository/MachineRepository.kt` — Added alarm command interface
- `data/repository/BleMachineRepository.kt` — Implemented alarm commands
- `data/repository/MockMachineRepository.kt` — Implemented alarm commands with simulated delay
- `ui/run/RunViewModel.kt` — Added `startGating` flow
- `ui/run/RunScreen.kt` — Passed `startGating` to content
- `ui/run/RunSections.kt` — Updated `ControlsSection` signature
- `ui/alarms/AlarmsViewModel.kt` — Full rewrite with command methods
- `ui/alarms/AlarmsScreen.kt` — Enabled acknowledge/clear functionality

### Tests Added
- `AlarmsViewModelTest.kt` — 7 tests for acknowledge/clear commands and connection state

### How to Test
1. Build and install: `./gradlew installDebug`
2. Navigate to Run screen
3. (Mock mode) Start button should be enabled (mock defaults to LIVE)
4. Navigate to Alarms screen
5. (Mock mode) No alarms by default; add mock alarms to test acknowledge
6. With alarms present, Acknowledge button should be enabled when connected
7. Tap Acknowledge — should mark alarm as acknowledged
8. Tap Clear Latched — should remove acknowledged/cleared alarms

### Next Steps (Stage 6)
1. Wire Devices screen to real BLE scanning/connecting
2. Add reconnection logic on unexpected disconnect
3. Surface session lease timeout warnings in UI

---

## Stage 6 — BLE Reconnection + Device Persistence

**Date:** 2026-01-16
**Branch:** `feature/stage-6-ble-reconnection`
**Status:** Complete

### Scope Statement
Implement auto-reconnection on unexpected BLE disconnect, persist last connected device for quick reconnection, and add connection feedback UI.

### Features Implemented

#### Device Preferences Persistence
- `DevicePreferences` DataStore for persisting:
  - Last connected device (address, name)
  - Auto-reconnect preference toggle
- Saved automatically when device connects
- Cleared when user taps "Forget"

#### Auto-Reconnection Logic
- `BleManager` tracks user-initiated vs unexpected disconnects
- `DisconnectEvent` emitted when device disconnects
- `DevicesViewModel` handles reconnection:
  - Up to 3 reconnect attempts with 2-second delay
  - Only reconnects on unexpected disconnects
  - Respects auto-reconnect user preference

#### Devices Screen Enhancements
- **Last Connected Device Card**: Shows when disconnected with saved device
  - "Reconnect" button to quickly reconnect
  - "Forget" button to clear saved device
- **Auto-reconnect Toggle**: Switch to enable/disable auto-reconnection
- **Connection Feedback Snackbars**:
  - "Connection lost to X. Reconnecting..."
  - "Reconnected successfully."
  - "Failed to reconnect to X."

### Architecture

```
data/preferences/
└── DevicePreferences.kt    # DataStore for device settings

data/ble/
└── BleManager.kt           # Added disconnect events, user-initiated tracking

ui/devices/
├── DevicesViewModel.kt     # Auto-reconnect logic, UI events
└── DevicesScreen.kt        # Last device card, auto-reconnect toggle
```

### Files Created
- `data/preferences/DevicePreferences.kt` — 65 lines

### Files Modified
- `gradle/libs.versions.toml` — Added DataStore dependency
- `app/build.gradle.kts` — Added DataStore dependency
- `data/ble/BleManager.kt` — Added disconnect events, `DisconnectEvent` class
- `ui/devices/DevicesViewModel.kt` — Full rewrite with auto-reconnect
- `ui/devices/DevicesScreen.kt` — Last device card, snackbars, toggle

### How to Test
1. Build and install: `./gradlew installDebug`
2. Navigate to Devices screen
3. (No BLE device) Shows empty "Last connected" since no device saved
4. (With BLE device) Connect to a device
5. Device info is persisted to DataStore
6. Force disconnect (turn off controller)
7. Observe snackbar "Connection lost... Reconnecting..."
8. After 3 failed attempts, see "Failed to reconnect"
9. Tap "Reconnect" to manually retry
10. Tap "Forget" to clear saved device
11. Toggle auto-reconnect off to disable automatic retry

### Next Steps (Stage 7)
1. Add session lease timeout warning banner
2. Surface heartbeat health in Status Strip
3. Add connection quality indicator (RSSI history)

---

## Stage 7 — Session Health + Signal Quality

**Date:** 2026-01-16
**Branch:** `feature/stage-7-session-health`
**Status:** Complete
**Merged to dev:** 2026-01-16

### Scope Statement
Implement session lease health monitoring with warning banners, surface heartbeat status in the Status Strip with visual indicators, and add RSSI history tracking for connection quality visualization.

### Features Implemented

#### Session Lease Monitoring
- `SessionLeaseStatus` enum: `NO_SESSION`, `OK`, `WARNING`, `EXPIRED`
- Warning triggered at 70% of lease time (`LEASE_WARNING_THRESHOLD`)
- Expired when lease age exceeds lease duration
- `SessionLeaseWarningBanner` component:
  - Animated expand/collapse visibility
  - Warning (amber): "Session approaching timeout. Heartbeat may be delayed."
  - Expired (red): "Session expired. Commands may not be accepted. Check connection."

#### Session Lease Chip in Status Strip
- Shows when session is in WARNING or EXPIRED state
- Displays percentage of lease consumed
- Color-coded: amber for warning, red for expired
- Warning icon for visual emphasis

#### Signal Quality Indicator
- `SignalQuality` enum: `UNKNOWN`, `POOR`, `FAIR`, `GOOD`, `EXCELLENT`
- RSSI thresholds: Excellent >= -50 dBm, Good >= -65 dBm, Fair >= -80 dBm
- RSSI history tracking (last 10 readings for smoothed quality)
- `SignalBars` composable: 4-bar indicator with filled/unfilled bars
- Updated `ConnectionChip` to show signal bars and quality description

#### RSSI Polling
- `BleManager.readRssi()` requests RSSI from connected device
- RSSI poll job runs every 2 seconds when session is active
- RSSI values collected and stored in history
- Average RSSI computed for stable quality indication

### Architecture

```
domain/model/
├── SystemStatus.kt            # Added session lease fields, RSSI history, signal quality

data/ble/
├── BleManager.kt              # Added readRssi(), onReadRemoteRssi callback, rssi StateFlow

data/repository/
└── BleMachineRepository.kt    # RSSI polling job, RSSI history tracking, lease age tracking

ui/components/
├── StatusStrip.kt             # Added SessionLeaseChip, SignalBars, updated ConnectionChip
└── SessionLeaseWarningBanner.kt  # New animated warning banner

ui/
└── ShakerControlApp.kt        # Added SessionLeaseWarningBanner to main layout
```

### SystemStatus Enhancements
- `sessionLeaseMs: Int` — Lease duration from MCU (default 3000ms)
- `sessionLeaseAgeMs: Long` — Time since last keepalive
- `rssiHistory: List<Int>` — Recent RSSI readings
- `sessionLeaseStatus: SessionLeaseStatus` — Computed lease health
- `averageRssi: Int?` — Smoothed RSSI from history
- `signalQuality: SignalQuality` — Computed from average RSSI

### Files Created
- `ui/components/SessionLeaseWarningBanner.kt` — 90 lines

### Files Modified
- `domain/model/SystemStatus.kt` — Added session lease and signal quality models
- `data/ble/BleManager.kt` — Added RSSI reading capability
- `data/repository/BleMachineRepository.kt` — Added RSSI polling, session tracking
- `ui/components/StatusStrip.kt` — Added SessionLeaseChip, SignalBars
- `ui/ShakerControlApp.kt` — Added warning banner to layout

### How to Test
1. Build and install: `./gradlew installDebug`
2. Navigate to any screen
3. (Without BLE device) Status shows "Disconnected" with no signal bars
4. (With BLE device) Connect to device
5. Observe:
   - Signal bars appear in connection chip (0-4 bars based on RSSI)
   - Signal quality text shows "Excellent/Good/Fair/Poor" with dBm
   - RSSI updates every 2 seconds
6. To test session lease warning:
   - Simulate keepalive failure (requires MCU or mock)
   - At 70% lease time: amber "Session: Stale" chip appears
   - Banner shows: "Session approaching timeout..."
   - At 100% lease time: red "Session: Expired" chip appears
   - Banner shows: "Session expired. Commands may not be accepted."

### Protocol Details
- RSSI read via `BluetoothGatt.readRemoteRssi()`
- Result arrives via `onReadRemoteRssi` callback
- Polling interval: 2000ms (separate from keepalive)
- History size: 10 readings for smoothing

### Next Steps (Stage 8)
1. Add recipe persistence and SD card management
2. Implement recipe transfer to MCU
3. Add QR code recipe import

---

## BLE Integration Testing

**Date:** 2026-01-16
**Branch:** `dev`
**Status:** Complete

### Scope Statement
Fix BLE communication issues discovered during real-device testing with ESP32 firmware. Add comprehensive unit tests for wire protocol and parsers.

### Issues Fixed

| Issue | Symptom | Root Cause | Fix |
|-------|---------|------------|-----|
| OPEN_SESSION failures | ACK returned null intermittently | GATT not fully stable when CONNECTED emitted | 200ms delay before openSession() |
| Device name showing "not connected" | Diagnostics page showed wrong name | Device name not tracked after connection | Added connectedDevice collector to BleMachineRepository |
| RSSI showing N/A | Diagnostics showed no signal strength | RSSI polling only started after successful session | Fixed by resolving session timing issue |
| Firmware version "Unknown" | Diagnostics showed unknown | Device Info characteristic read but not parsed | Added DeviceInfo parser with StateFlow |
| Protocol version "Unknown" | Diagnostics showed unknown | Same as above | Same fix |

### Features Implemented

#### Device Info Parsing
- `DeviceInfo` data class with `parse()` function for 12-byte characteristic
- Layout: `proto_ver(u8) + fw_major(u8) + fw_minor(u8) + fw_patch(u8) + build_id(u32) + cap_bits(u32)`
- `BleManager.deviceInfo` StateFlow exposes parsed info
- `BleMachineRepository` wires to `SystemStatus.firmwareVersion` and `protocolVersion`

#### Unit Tests Added
- `DeviceInfoTest.kt` - 12 tests for Device Info characteristic parsing
- `WireProtocolTest.kt` - 20 tests for CRC-16 and frame encoding/decoding
- `ParserTest.kt` - 35 tests for telemetry, ACK, event, and payload parsing
- Total: 87 unit tests, all passing

### Files Created
- `app/src/test/kotlin/com/shakercontrol/app/data/ble/DeviceInfoTest.kt`
- `app/src/test/kotlin/com/shakercontrol/app/data/ble/WireProtocolTest.kt`
- `app/src/test/kotlin/com/shakercontrol/app/data/ble/ParserTest.kt`
- `CLAUDE.md` - Project guide with UI test coordinates

### Files Modified
- `data/ble/BleConstants.kt` - Added DeviceInfo data class and parser
- `data/ble/BleManager.kt` - Added deviceInfo StateFlow, parsing in onCharacteristicRead
- `data/repository/BleMachineRepository.kt` - Added 200ms delay, device info and name collectors
- `docs/FIRMWARE_AGENT_PROMPT.md` - Updated with testing status and current issues

### How to Test
1. `./gradlew test` - Run all 87 unit tests
2. With real ESP32 MCU:
   - Connect to "SYS-CTRL-XXXX" device
   - Verify session opens successfully in logcat
   - Check Diagnostics page shows:
     - Device name: SYS-CTRL-XXXX
     - RSSI: -XX dBm
     - Firmware version: X.Y.Z
     - Protocol version: N

### Real Device Testing Results
Tested on Lenovo TB351FU tablet (Android 16) with ESP32-S3 MCU:
- ✅ BLE scan and connect working
- ✅ OPEN_SESSION ACK received reliably
- ✅ KEEPALIVE heartbeat running at 1Hz
- ✅ Telemetry streaming at 10Hz
- ✅ RSSI polling at 0.5Hz
- ✅ Diagnostics page fully populated

---

## Stage 8 — I/O Control + Recipe Features

**Date:** 2026-01-16
**Branch:** `dev` (in progress)
**Status:** Partial

### Scope Statement
Add I/O Control screen for direct relay output control and digital input visualization. Prepare for recipe persistence features.

### Features Implemented

#### I/O Control Screen
- **Digital Inputs card**: Shows DI1-DI8 with LED-style indicators
  - Green circle with "H" for HIGH state
  - Gray circle with "L" for LOW state
  - Simulation toggle (service mode only) to test input logic
- **Relay Outputs card**: Shows RO1-RO8 with status and control buttons
  - Status indicator showing current ON/OFF state
  - Toggle buttons to control each relay
  - Requires Service Mode to enable controls
- **Service Mode hint**: Banner at bottom explaining how to enable controls

#### Repository Interface Extensions
- `setRelay(channel, on)`: Send relay control command to MCU
- `isSimulationEnabled`: StateFlow for simulation mode state
- `setSimulationEnabled(enabled)`: Toggle input simulation
- `setSimulatedInput(channel, high)`: Set simulated input values

#### Navigation
- Added "I/O Control" to navigation drawer between Alarms and Diagnostics
- Route: `NavRoutes.Io` at path "io"

### Architecture

```
ui/io/
├── IoViewModel.kt         # ViewModel with relay control and simulation logic
└── IoScreen.kt            # UI with DI/RO visualization

data/repository/
├── MachineRepository.kt   # Added setRelay, simulation methods
├── BleMachineRepository.kt # BLE implementation with real/simulated input tracking
└── MockMachineRepository.kt # Mock implementation for testing
```

### Files Created
- `ui/io/IoViewModel.kt` — 107 lines
- `ui/io/IoScreen.kt` — 324 lines
- `test/ui/io/IoViewModelTest.kt` — 249 lines (14 tests)

### Files Modified
- `data/repository/MachineRepository.kt` — Added I/O interface methods
- `data/repository/BleMachineRepository.kt` — BLE relay command, simulation tracking
- `data/repository/MockMachineRepository.kt` — Mock implementation
- `ui/navigation/NavRoutes.kt` — Added Io route
- `ui/navigation/AppNavHost.kt` — Added IoScreen composable
- `ui/ShakerControlApp.kt` — Added I/O Control to drawer

### Tests Added
- 14 new unit tests in `IoViewModelTest.kt`
- Total tests: 101 (was 87)

### Protocol Details
- `SET_RELAY` command (0x0022): Channel (1-8) + state (ON=1/OFF=0)
- Optimistic UI update on successful ACK
- Service mode required for relay control (enforced in UI)

### How to Test
1. `./gradlew testDebugUnitTest` — Run all 101 tests
2. Install on device and navigate to I/O Control via drawer
3. (Without service mode) Observe inputs, relay buttons disabled
4. Enable Service Mode from drawer
5. Toggle relays — should send commands to MCU
6. Enable Simulate toggle — tap DI LEDs to toggle simulated state

### Known Issues
- Connect popup on first start doesn't initiate connection sequence
- Relay commands show "NO ARGS" error - firmware needs SET_RELAY handler

### Next Steps
1. Recipe persistence (save/load recipes to local storage)
2. Recipe transfer to MCU (if applicable)
3. QR code recipe import (future feature)

---

## Stage 8 Progress Update (2025-01-16)

### Navigation Architecture Improvements

#### Back Button Simplification
Implemented a cleaner navigation architecture for back button handling:

1. **Top-level pages (drawer accessible)**: Never show back button
   - Home, Run, Devices, Alarms, I/O Control, Diagnostics, Settings
   - Marked with `isTopLevel = true` in NavRoutes

2. **Sub-pages (detail screens)**: Show back button
   - PID 1, PID 2, PID 3 detail screens
   - Marked with `isTopLevel = false` (default)

3. **Fixed layout shift**: Back button area is now a fixed 48dp Box with Crossfade animation
   - Menu icon or back arrow occupies the same space
   - No secondary menu button, eliminating visual shift

#### Connect Button Fix
- RunCard "Connect" button now navigates to Devices screen when disconnected
- Previously always navigated to Run screen regardless of connection state

### Files Modified
- `ui/navigation/NavRoutes.kt` — Added `isTopLevel` property to distinguish drawer vs detail pages
- `ui/ShakerControlApp.kt` — Updated back button logic: `currentRoute?.isTopLevel == false`
- `ui/components/StatusStrip.kt` — Simplified MenuBackButtonArea to fixed 48dp Box
- `ui/home/HomeScreen.kt` — Added `onNavigateToDevices` callback
- `ui/home/HomeCards.kt` — RunCard navigates to Devices when disconnected
- `ui/navigation/AppNavHost.kt` — Pass `onNavigateToDevices` to HomeScreen

### Firmware Handoff
Updated `docs/FIRMWARE_AGENT_PROMPT.md` with SET_RELAY (0x0001) implementation requirements:
- Command frame structure with relay_index and state
- Expected ACK format
- Testing procedure from Android app

### Testing Results
- All 101 unit tests passing
- Navigation tested on device - no visual shift on back button transitions
- Connect button correctly navigates to Devices when disconnected

---

## Stage 8 Progress Update (2026-01-19) — PID Error Handling & Capability Editing

### PID ID Mapping Update
Updated PID controller ID assignments to match physical hardware:
- **PID 1**: LN2 (Cold) — Optional, cryogenic temperature control
- **PID 2**: Axle bearings — Required, main bearing temperature
- **PID 3**: Orbital bearings — Required, orbital mechanism temperature

### Probe Error Detection
Implemented probe error detection for Omron E5CC PID controllers:

#### PidData.kt Enhancements
- Added `ProbeError` enum: `NONE`, `OVER_RANGE` (HHHH), `UNDER_RANGE` (LLLL)
- Added `probeError` field to `PidData` model
- Added `hasProbeError` computed property
- Added `hasAnyIssue` computed property (combines offline/stale/fault/probe error)
- Added `detectProbeError(pv, isLn2Controller)` static helper

#### Threshold Tuning
- `PROBE_ERROR_THRESHOLD_HIGH`: Lowered from 3000°C to 500°C to catch E5CC probe errors (~800°C observed)
- `PROBE_ERROR_THRESHOLD_LOW`: -300°C (unchanged, only applies to non-LN2 controllers)
- `STALE_THRESHOLD_MS`: Increased from 500ms to 1500ms to accommodate 900ms RS-485 polling interval

**Note**: Both threshold changes are documented in code comments with date and rationale. Can be reverted if needed.

### Visual Error Treatment (RunSections.kt)
- **Pulsing red border**: For offline or probe error states
- **Yellow border**: For stale status (data age warning)
- **Status badges**: OFFLINE, HHHH, LLLL, STALE shown in PID tiles
- **PV display**: Shows error code (HHHH/LLLL) instead of temperature when probe has error
- **Fault LED**: Lights up when probe error detected

### Capability Editing in Service Mode (DiagnosticsScreen.kt)
- Capability chips now editable when in service mode
- Tap any capability chip → dropdown with all levels (Required/Optional/Not installed/Simulated)
- **OVERRIDE badge**: Shows when capabilities have been modified from defaults
- **Reset to Defaults button**: Clears all overrides
- Overrides persist while in service mode but can be cleared

### Repository Interface Extensions
Added to `MachineRepository`:
```kotlin
val capabilityOverrides: StateFlow<SubsystemCapabilities?>
suspend fun setCapabilityOverride(subsystem: String, level: CapabilityLevel)
suspend fun clearCapabilityOverrides()
```

Implemented in both `BleMachineRepository` and `MockMachineRepository`.

### DiagnosticsViewModel Updates
- Added `hasCapabilityOverrides` StateFlow
- Added `setCapabilityOverride(subsystem, level)` function
- Added `clearCapabilityOverrides()` function

### Files Modified
- `domain/model/PidData.kt` — ProbeError enum, detection logic, threshold constants
- `domain/model/SubsystemCapabilities.kt` — Updated comments for PID ID mapping
- `data/repository/MachineRepository.kt` — Capability override interface
- `data/repository/BleMachineRepository.kt` — Probe detection, capability levels
- `data/repository/MockMachineRepository.kt` — Capability override implementation
- `ui/run/RunSections.kt` — Visual error treatment, pulsing borders, status badges
- `ui/diagnostics/DiagnosticsScreen.kt` — Editable capability chips
- `ui/diagnostics/DiagnosticsViewModel.kt` — Capability editing methods
- `ui/home/HomeScreen.kt` — Updated preview with new PID names
- `ui/home/HomeCards.kt` — Updated PID ID references
- `ui/pid/PidDetailScreen.kt` — Updated PID ID references
- Tests: Updated PID names in `MockMachineRepositoryTest.kt`, `PidDetailViewModelTest.kt`

### Testing
- All 101 unit tests passing
- Real device testing with ESP32-S3 MCU:
  - PID 2 and PID 3 reading proper values
  - PID 1 showing 800°C (disconnected probe) → now triggers HHHH error display
  - Stale cycling fixed with 1500ms threshold

### Known Issues Resolved
- PID stale cycling: Was caused by 500ms threshold vs 900ms RS-485 poll interval → fixed with 1500ms threshold
- Probe error not detected: Was caused by 3000°C threshold vs ~800°C E5CC error value → fixed with 500°C threshold

---

## Lazy Polling Integration

**Date:** 2025-01-20
**Status:** App-side complete, pending firmware telemetry extension

### Overview
Implemented lazy polling feature to reduce RS-485 coil whine when system is idle. The MCU reduces PID controller polling frequency after a configurable idle timeout.

### App-Side Implementation (Complete)

#### Commands Added
- `SET_IDLE_TIMEOUT (0x0040)`: Set idle timeout in minutes (0=disabled, 1-255=enabled)
- `GET_IDLE_TIMEOUT (0x0041)`: Query current idle timeout from MCU

#### Files Modified
- `BleConstants.kt`: Added command IDs
- `MachineRepository.kt`: Added interface methods
  ```kotlin
  val mcuIdleTimeoutMinutes: StateFlow<Int?>
  suspend fun setIdleTimeout(minutes: Int): Result<Unit>
  suspend fun getIdleTimeout(): Result<Int>
  ```
- `BleMachineRepository.kt`: Full implementation with command sending
- `MockMachineRepository.kt`: Mock implementation
- `SettingsViewModel.kt`: UI binding with MCU sync on connect
- `SettingsScreen.kt`: Lazy Polling section with toggle and timeout picker
- `DiagnosticsScreen.kt`: Added "RS-485 Polling" card showing current state

#### Telemetry Parsing (Ready)
- `WireProtocol.kt`: Added `RunStateData` parsing for 16-byte `wire_telemetry_run_state_t`
- Fields parsed: `lazyPollActive` (bool), `idleTimeoutMin` (u8)
- App preserves local values when firmware doesn't send extended telemetry

#### UI
- **Settings → Lazy Polling**: Toggle + dropdown (1-60 minutes)
- **Diagnostics → RS-485 Polling card**: Shows FAST/SLOW chip, polling mode, idle timeout

### MCU as Source of Truth (2025-01-20 Update)

**Issue:** When MCU reboots, app showed stale idle timeout (from local prefs) instead of MCU's actual value.

**Fix:** App now queries GET_IDLE_TIMEOUT on connect to sync from MCU.

In `BleMachineRepository.kt`:
```kotlin
private fun queryMcuIdleTimeout() {
    scope.launch {
        val result = getIdleTimeout()
        result.onSuccess { minutes ->
            _mcuIdleTimeoutMinutes.value = minutes
            _systemStatus.value = currentStatus.copy(idleTimeoutMinutes = minutes)
        }
    }
}
```

Called from `openSession()` after session established.

**Design principle:** MCU is authoritative. App only:
- Sends SET_IDLE_TIMEOUT commands
- Queries GET_IDLE_TIMEOUT on connect
- Displays MCU's reported state

### Firmware v0.3.7+ Integration (2026-01-20)

**Firmware changes (v0.3.7-v0.3.10):**
- v0.3.7: Bug fix - KEEPALIVE no longer resets idle timer
- v0.3.8: Added `lazy_poll_active` and `idle_timeout_min` to telemetry
- v0.3.9: Added reserved byte to complete 16-byte run state
- v0.3.10: Adjusted stale/offline thresholds for lazy mode stability
- New command IDs: `SET_LAZY_POLL` (0x0060), `GET_LAZY_POLL` (0x0061)
- New payload format: 2 bytes `[enable (u8), timeout_min (u8)]`
- NVS persistence for idle timeout setting

**App changes:**
- Updated `BleConstants.kt`: Added both new (0x0060/0x0061) and legacy (0x0040/0x0041) command IDs
- Updated `BleMachineRepository.kt`:
  - `setIdleTimeout()` tries new format first, falls back to legacy
  - `getIdleTimeout()` tries new format first, falls back to legacy
  - Passes `lazyPollActive` to PidData for dynamic thresholds
- Updated `PidData.kt`:
  - Added `lazyPollActive` field
  - Dynamic stale/offline thresholds based on polling mode:
    - Fast mode: STALE=1500ms, OFFLINE=3000ms
    - Lazy mode: STALE=7000ms, OFFLINE=12000ms

**Testing:**
1. Connect app with lazy polling enabled (1 min timeout)
2. Wait 1+ minutes with no user interaction
3. Verify Diagnostics shows "SLOW" and `lazy_poll_active = 1`
4. Send any user command → verify returns to "FAST" mode

### Alarm History Tracking (Complete)

#### Purpose
MCU reports real-time `alarm_bits` (not latched). App must track alarm transitions for:
- Catching transient alarms that clear before user notices
- Showing alarm history for debugging

#### Implementation
- `Alarm.kt`: Added `AlarmHistoryEntry`, `AlarmBitDefinitions`
- `MachineRepository.kt`: Added `alarmHistory`, `unacknowledgedAlarmBits`, `acknowledgeAlarmBits()`, `clearAlarmHistory()`
- `BleMachineRepository.kt`: `trackAlarmTransitions()` records all bit changes

### Files Summary
| File | Changes |
|------|---------|
| `BleConstants.kt` | Command IDs 0x0040, 0x0041 |
| `WireProtocol.kt` | `RunStateData` parsing with lazy polling fields |
| `MachineRepository.kt` | Lazy polling + alarm history interfaces |
| `BleMachineRepository.kt` | Full implementation |
| `MockMachineRepository.kt` | Mock implementations |
| `SettingsViewModel.kt` | MCU sync, setters |
| `SettingsScreen.kt` | Lazy Polling UI section |
| `DiagnosticsScreen.kt` | RS-485 Polling status card |
| `SystemStatus.kt` | `lazyPollActive`, `idleTimeoutMinutes` fields |
| `Alarm.kt` | `AlarmHistoryEntry`, `AlarmBitDefinitions` |

### Testing
- All 101 unit tests passing
- SET_IDLE_TIMEOUT command confirmed working (ACK received)
- Value persists in app after setting
- Firmware not sending extended telemetry (known issue → firmware handoff)

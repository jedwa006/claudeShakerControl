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

**Date:** TBD
**Branch:** `feature/stage-5-capabilities-alarms`
**Status:** Not Started

*(To be filled when Stage 5 begins)*

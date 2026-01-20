# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Complete UI skeleton for Android tablet HMI
  - Home dashboard with Run, Temperatures, Status, Diagnostics, and Settings cards
  - Run cockpit with recipe editor, controls, PID tiles, and indicators
  - Devices screen for BLE pairing (placeholder)
  - PID detail pages for per-loop tuning
  - Alarms list with active/history views
  - Diagnostics screen showing connection health and firmware info
  - Settings screen with theme and about sections
- StatusStrip component with connection status, MCU heartbeat, machine state, alarm count
- LedIndicator component with pulsing animation for active outputs
- ServiceModeBanner for warning display in service mode
- Mock data repository for UI development
- Material 3 dark theme with semantic colors (Normal, Warning, Alarm, Stale)
- Modal navigation drawer with screen selection
- BLE communication infrastructure (Stage 2)
  - BleConstants with GATT UUIDs, message types, command IDs
  - WireProtocol frame encoder/decoder with CRC-16/CCITT-FALSE
  - BleManager for scan/connect/disconnect operations
  - BleMachineRepository implementing MachineRepository with BLE backend
  - Session/lease management (OPEN_SESSION, KEEPALIVE at 1Hz)
  - Telemetry, event, and ACK parsing
- Real Devices screen with scan/connect functionality
  - Device list showing name, address, RSSI
  - Connection state display
- Run workflow functionality (Stage 3)
  - Live timer countdown during runs with phase/cycle tracking
  - Timer correctly pauses and resumes from exact point
  - Command feedback with error snackbar on failures
  - Loading indicator while commands are executing
  - Buttons disabled during command execution
- PID detail pages with real controls (Stage 4)
  - Setpoint editing with text input and Apply button
  - Mode control with segmented button (Stop/Manual/Auto)
  - Command feedback with loading indicators and error snackbar
  - Controls disabled when not connected
  - Color-coded mode selection
  - SET_SV and SET_MODE BLE commands implemented

### Fixed
- PAUSE_RUN command now uses correct command ID (0x0104) instead of STOP_RUN

### Infrastructure
- Android project with Kotlin 2.0.21 + Jetpack Compose
- Hilt dependency injection
- Gradle 8.9 with version catalog
- BLE permissions configured (Android 12+)

### Documentation
- Project documentation scaffolding
  - `AGENTS.md` — Operational guide for AI agents
  - `docs/AGENT_LOG.md` — Development decisions and stage tracking
  - `docs/system-inventory.md` — Hardware inventory and capability bits
- Framework decision: Kotlin + Jetpack Compose (Native Android)
- Staged implementation plan (Stages 0–5)
- Consolidated MCU protocol documentation in `docs/MCU_docs/`
- Dashboard UI specification (`docs/dashboard-sec-v1.md`)
- UI copy and labels (`docs/ui-copy-labels-v1.md`)

---

## Version History

*(Versions will be added as releases are made)*

<!--
Example format for future releases:

## [0.1.0] - 2026-XX-XX

### Added
- Initial UI skeleton with Home and Run screens
- Dark theme with Material 3
- Mock data backend for UI preview

### Changed
- ...

### Fixed
- ...

### Removed
- ...
-->

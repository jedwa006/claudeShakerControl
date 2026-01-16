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

# AGENTS.md — Operational Guide for AI Agents

This document serves as the persistent operational guide for all agents working on this project.

## Project Overview

**Project:** Cryogenic Shaker Ball Mill HMI (Android Tablet)
**Target:** Android 16 tablet (Lenovo Tab Plus, 11.5", 2000x1200, landscape-first)
**Communication:** BLE to ESP32-S3 (Waveshare ESP32-S3-POE-ETH-8DI-8RO)
**Framework:** Kotlin + Jetpack Compose (Native Android)
**MCU Framework:** ESP-IDF (v5.5.2 sufficient; v6.0 not required)

## Golden Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run lint checks
./gradlew lintDebug

# Run unit tests (once tests exist)
./gradlew testDebugUnitTest

# Clean build
./gradlew clean

# Full verification (run before marking any stage done)
./gradlew clean assembleDebug lintDebug testDebugUnitTest
```

## Repo Conventions

### Branching Model
- `main` — protected, release-ready (no direct commits)
- `dev` — integration branch (features merge here first)
- Feature branches:
  - `feature/<short-scope>`
  - `fix/<short-scope>`
  - `chore/<short-scope>`
  - `docs/<short-scope>`

### Commit Style
Use [Conventional Commits](https://www.conventionalcommits.org/):
- `feat:` new feature
- `fix:` bug fix
- `docs:` documentation only
- `chore:` maintenance/tooling
- `refactor:` code restructure without behavior change
- `test:` adding/modifying tests

### Key Documentation Locations
| Document | Location | Purpose |
|----------|----------|---------|
| Agent Log | `docs/AGENT_LOG.md` | Decisions, assumptions, PR notes per stage |
| Changelog | `CHANGELOG.md` | User-facing changes |
| Architecture | `docs/architecture.md` | High-level system architecture |
| UI Spec | `docs/dashboard-sec-v1.md` | Screen layouts, components, behavior |
| UI Copy | `docs/ui-copy-labels-v1.md` | All UI text and labels |
| System Inventory | `docs/system-inventory.md` | Sensors, peripherals, capability bits |
| MCU Protocol | `docs/MCU_docs/` | GATT schema, wire protocol, commands |

### Screenshot Storage
Save emulator/device screenshots to:
```
docs/screenshots/<YYYY-MM-DD>/<feature-scope>/
```
Name files descriptively (e.g., `home-card-run.png`, `run-cockpit-paused.png`).

## Stop Conditions

**STOP and ask for direction if:**
1. Build/BLE issue persists after 2 failed fix attempts
2. BLE protocol details are insufficient to proceed
3. A change would touch more than ~10 files for a single goal
4. Safety-related behavior is ambiguous
5. You need to install system packages or change global settings

## Key Architecture Decisions (Locked)

1. **MCU is authoritative** — App is UI client only; no "smart control" that could conflict with MCU safety logic
2. **Capability bits sync on connect** — App reads capabilities once on first connection; MCU controls enforcement
3. **Commands sent optimistically** — App sends commands; MCU rejects if invalid (with error display in app)
4. **Hold timing** — `HOLD × (cycles - 1)` (no hold after final milling phase)
5. **Pause behavior** — Current phase timer freezes; resumes from same point
6. **Service mode** — Warning-acknowledged (2-step), does not persist across app restarts

## BLE Protocol Quick Reference

### Service UUID
`F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E60`

### Characteristics
| Name | UUID Suffix | Properties |
|------|-------------|------------|
| Device Info | `...5E61` | Read |
| Telemetry Stream | `...5E62` | Notify |
| Command RX | `...5E63` | Write |
| Events + Acks | `...5E64` | Indicate/Notify |
| Bulk Gateway | `...5E65` | Write/Notify (future) |
| Diagnostic Log | `...5E66` | Notify (optional) |

### Wire Protocol
- Endianness: Little-endian
- Scaling: PV/SV ×10, OP% ×10
- CRC: CRC-16/CCITT-FALSE
- See `docs/MCU_docs/90-command-catalog.md` for full frame format

## Staged Implementation Plan

| Stage | Scope | Key Deliverables |
|-------|-------|------------------|
| 0 | Alignment + Plan | This doc, AGENT_LOG, CHANGELOG, system inventory |
| 1 | Bootstrap + UI Skeleton | Dark theme, nav scaffold, Home/Run screens, mock data |
| 2 | BLE Foundation | Permissions, scan/connect, heartbeats, auto-reconnect |
| 3 | Run Workflow | Recipe editor, run controls, live progress |
| 4 | PID Summaries + Pages | PID tiles, detail pages, data binding |
| 5 | Capability Gating + Alarms | UI gating, alarm banner/list |

## Escalation Rules

Before making changes that affect:
- System packages or global settings → **ASK FIRST**
- Security/safety behavior → **ASK FIRST**
- BLE protocol format → **Document in AGENT_LOG, propose change**
- More than 10 files → **Propose split into sub-branches**

## Android 16 BLE Notes

- Use `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` permissions (Android 12+ model)
- Consider `CompanionDeviceManager` for device association
- Location permission NOT required if using `neverForLocation` flag
- Implement scan timeouts, backoff, and clear error UX

## Session Resume Protocol

If conversation is interrupted:
1. Read `docs/AGENT_LOG.md` for last completed stage and current state
2. Check git branch and last commit
3. Review TODO items in current stage
4. Continue from documented checkpoint

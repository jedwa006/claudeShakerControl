# MCU Agent Handoff: Safety Gates & State Machine Integration

**Date:** 2026-01-20
**App Version:** Post-alarm/lazy-polling integration
**Status:** App-side safety gating implemented, MCU enforcement needed

---

## Summary

The Android app now implements app-side safety gating for probe errors and provides user confirmation dialogs in service mode. However, **the MCU must be the authoritative safety enforcer** since app-side checks can be bypassed or may not apply if the controller is accessed via other means.

This document describes what the MCU needs to implement to complete the safety system.

---

## Current App-Side Implementation

### 1. Start Gating (RunViewModel.kt)

The app blocks `START_RUN` commands when:
- Connection state is not `LIVE`
- Machine state doesn't allow start (`FAULT`, `E_STOP`, `RUNNING`, `PAUSED`)
- Required PIDs are offline (ageMs > threshold)
- **NEW:** Required PIDs have probe errors (HHHH/LLLL)

```kotlin
// Probe error check added to checkStartGating()
val requiredPidsWithProbeError = pids.filter { pid ->
    val capability = capabilities.getPidCapability(pid.controllerId)
    capability == CapabilityLevel.REQUIRED && pid.hasProbeError
}

if (requiredPidsWithProbeError.isNotEmpty()) {
    return StartGatingResult.blocked("${errorPid.name} probe error ($errorType).")
}
```

### 2. Service Mode Controller Enable (Heat/Cool Buttons)

When enabling a PID controller in service mode:
- App checks if the controller has a probe error
- If yes, shows a warning dialog requiring user confirmation
- User can choose "Enable Anyway" to proceed despite the warning
- Disabling (AUTO → STOP) never requires confirmation

### 3. Probe Error Detection

App detects probe errors based on PV thresholds:
- **HHHH (Over-range):** PV >= 500°C (actual E5CC shows ~800°C when disconnected)
- **LLLL (Under-range):** PV <= -300°C (excluded for LN2 controller)

These are logged to alarm history with `AlarmHistorySource.APP_PROBE_ERROR`.

---

## Required MCU Implementation

### 1. Safety Gate Enforcement on Commands

The MCU should validate commands and reject unsafe operations. Suggested implementation:

#### SET_MODE (0x0020) Command Validation

When receiving `SET_MODE(controller_id, AUTO)`:

```c
typedef struct {
    bool estop_active;           // From DI or alarm bits
    bool pid_offline[3];         // RS-485 comm timeout
    bool pid_probe_error[3];     // PV out of range (HHHH/LLLL)
    bool pid_fault[3];           // Controller reports fault
    bool safety_override;        // NVS-persisted override flag
} safety_state_t;

cmd_status_t validate_set_mode_auto(uint8_t controller_id, safety_state_t* safety) {
    // Always block during E-Stop
    if (safety->estop_active) {
        return CMD_STATUS_REJECTED_ESTOP;
    }

    // Block if controller offline (RS-485 not responding)
    if (safety->pid_offline[controller_id - 1]) {
        return CMD_STATUS_REJECTED_OFFLINE;
    }

    // Block if controller has fault
    if (safety->pid_fault[controller_id - 1]) {
        return CMD_STATUS_REJECTED_FAULT;
    }

    // Block probe error UNLESS safety override enabled
    if (safety->pid_probe_error[controller_id - 1] && !safety->safety_override) {
        return CMD_STATUS_REJECTED_PROBE_ERROR;
    }

    return CMD_STATUS_OK;
}
```

#### START_RUN (0x0010) Command Validation

When receiving `START_RUN`:

```c
cmd_status_t validate_start_run(safety_state_t* safety, capability_bits_t caps) {
    // Always block during E-Stop
    if (safety->estop_active) {
        return CMD_STATUS_REJECTED_ESTOP;
    }

    // Check each required PID
    for (int i = 0; i < 3; i++) {
        if (!is_pid_required(i + 1, caps)) continue;

        if (safety->pid_offline[i]) {
            return CMD_STATUS_REJECTED_OFFLINE;
        }
        if (safety->pid_fault[i]) {
            return CMD_STATUS_REJECTED_FAULT;
        }
        // Probe error blocks start even with override (safety critical)
        if (safety->pid_probe_error[i]) {
            return CMD_STATUS_REJECTED_PROBE_ERROR;
        }
    }

    return CMD_STATUS_OK;
}
```

### 2. New ACK Status Codes

Add detailed rejection reasons to the ACK response:

```c
typedef enum {
    CMD_STATUS_OK = 0x00,
    CMD_STATUS_INVALID_ARGS = 0x01,
    CMD_STATUS_NO_SESSION = 0x02,
    CMD_STATUS_REJECTED = 0x03,
    // New safety-specific rejection codes
    CMD_STATUS_REJECTED_ESTOP = 0x10,
    CMD_STATUS_REJECTED_OFFLINE = 0x11,
    CMD_STATUS_REJECTED_FAULT = 0x12,
    CMD_STATUS_REJECTED_PROBE_ERROR = 0x13,
    CMD_STATUS_REJECTED_NOT_READY = 0x14,
} cmd_status_t;
```

### 3. Probe Error Detection on MCU

The MCU should detect probe errors from RS-485 readings:

```c
#define PROBE_ERROR_HIGH_THRESHOLD  5000  // 500.0°C × 10
#define PROBE_ERROR_LOW_THRESHOLD  -3000  // -300.0°C × 10 (not for LN2)

bool detect_probe_error(int controller_id, int16_t pv_x10) {
    // Over-range (HHHH) for all controllers
    if (pv_x10 >= PROBE_ERROR_HIGH_THRESHOLD) {
        return true;
    }

    // Under-range (LLLL) only for heater controllers (2, 3)
    // LN2 controller (1) legitimately reads very low temperatures
    if (controller_id != 1 && pv_x10 <= PROBE_ERROR_LOW_THRESHOLD) {
        return true;
    }

    return false;
}
```

### 4. Safety Override Configuration

Add NVS-persisted safety override for testing:

#### New BLE Commands

**CMD_SET_SAFETY_OVERRIDE (0x0062)**

Request payload (1 byte):
```
Offset  Size  Field          Description
------  ----  -------------  ---------------------------
0       1     enable         1 = enable override, 0 = normal safety
```

Response: Standard ACK

**CMD_GET_SAFETY_OVERRIDE (0x0063)**

Request payload: None

Response payload (1 byte):
```
Offset  Size  Field          Description
------  ----  -------------  ---------------------------
0       1     enabled        1 = override active, 0 = normal
```

#### Behavior

- When `safety_override = true`:
  - SET_MODE(AUTO) allowed even with probe error
  - START_RUN still blocked by probe errors (never override for run)
  - E-Stop always blocks everything (never override)

- Setting persists to NVS
- Only accessible when service mode is acknowledged (future: add service mode pin)

### 5. Telemetry Updates

Consider adding probe error flags to telemetry for real-time MCU→App notification:

```c
// In wire_telemetry_header_t or controller_data
typedef struct {
    uint8_t controller_id;
    int16_t pv_x10;
    int16_t sv_x10;
    uint16_t op_x10;
    uint8_t mode;
    uint16_t age_ms;
    uint8_t flags;  // NEW: bit 0 = probe_error, bit 1 = fault, bit 2 = al1, bit 3 = al2
} wire_controller_data_t;
```

This allows the app to rely on MCU's probe error detection rather than duplicating the logic.

---

## State Machine Integration

### Machine States for Safety

```
IDLE ──────► READY ──────► RUNNING ──────► IDLE
  │            │              │
  │            │              ▼
  │            │          PAUSED ──────► RUNNING
  │            │              │
  ▼            ▼              ▼
E_STOP ◄──── FAULT ◄────────┘
```

### State Transitions with Safety Gates

| From | Command | Condition | To |
|------|---------|-----------|-----|
| IDLE | START_RUN | All safety checks pass | RUNNING |
| IDLE | START_RUN | E-Stop active | (rejected) |
| IDLE | START_RUN | Required PID offline/fault/probe error | (rejected) |
| RUNNING | (probe error detected) | Required PID | FAULT |
| RUNNING | (offline detected) | Required PID | FAULT |
| RUNNING | E-Stop | Any | E_STOP |
| ANY | SET_MODE(AUTO) | E-Stop active | (rejected) |
| ANY | SET_MODE(AUTO) | PID offline | (rejected) |
| ANY | SET_MODE(AUTO) | Probe error, no override | (rejected) |
| ANY | SET_MODE(AUTO) | Probe error, override enabled | (allowed) |

### Mid-Run Safety

If a required PID develops a fault or probe error during a run:

1. MCU should transition to FAULT state
2. Set alarm bit for the affected PID
3. Stop all PIDs (set mode to STOP)
4. App will see state change via telemetry and update UI

---

## Testing Checklist

### Basic Safety Gates
- [ ] SET_MODE(AUTO) rejected when E-Stop active
- [ ] SET_MODE(AUTO) rejected when PID offline
- [ ] SET_MODE(AUTO) rejected when probe error (override disabled)
- [ ] SET_MODE(AUTO) allowed when probe error (override enabled)
- [ ] START_RUN rejected when E-Stop active
- [ ] START_RUN rejected when required PID offline
- [ ] START_RUN rejected when required PID has probe error
- [ ] START_RUN rejected when required PID has fault

### Safety Override
- [ ] Safety override persists across reboot
- [ ] Safety override only affects SET_MODE, not START_RUN
- [ ] Safety override never bypasses E-Stop

### Mid-Run Protection
- [ ] Probe error on required PID during run → FAULT state
- [ ] Offline on required PID during run → FAULT state
- [ ] E-Stop during run → E_STOP state
- [ ] All PIDs set to STOP on fault/e-stop

### App Integration
- [ ] App shows specific rejection reason from ACK
- [ ] App confirmation dialog appears for probe error enable
- [ ] Start button disabled with probe error reason shown

---

## Relevant App Files

| Component | File Path |
|-----------|-----------|
| Start gating logic | `app/.../ui/run/RunViewModel.kt` (checkStartGating) |
| Confirmation dialog | `app/.../ui/run/RunScreen.kt` (ProbeErrorWarningDialog) |
| Probe error model | `app/.../domain/model/PidData.kt` (ProbeError, detectProbeError) |
| Alarm history | `app/.../domain/model/Alarm.kt` (AlarmHistoryEntry, AlarmHistorySource) |
| BLE constants | `app/.../data/ble/BleConstants.kt` (CommandId) |

---

## Summary

**App-side (Done):**
1. ✅ Start gating blocks when required PID has probe error
2. ✅ Confirmation dialog for enabling controller with probe error
3. ✅ Probe errors logged to alarm history

**MCU-side (Needed):**
1. ⬜ Validate SET_MODE commands against safety state
2. ⬜ Validate START_RUN commands against safety state
3. ⬜ Add detailed rejection status codes to ACK
4. ⬜ Detect probe errors from RS-485 readings
5. ⬜ Implement safety override (CMD_SET/GET_SAFETY_OVERRIDE)
6. ⬜ Handle mid-run safety events (→ FAULT state)
7. ⬜ Consider adding probe error flags to telemetry

The app provides a good UX layer but the MCU must be the safety authority. The app's checks are advisory and help users understand why operations fail; the MCU's checks are the actual enforcement.

---

## Additional TODOs (App + MCU Coordination)

### 1. Light/Door Switch Integration

The Run page has light and door switches that need to be connected to actual relay outputs:

**App-side:**
- Light switch → needs to toggle a specific relay (TBD which channel)
- Door switch → needs to toggle door lock relay (TBD which channel)
- Currently these are UI-only, not connected to `setRelay()` calls

**MCU-side:**
- Define relay channel assignments for light and door lock
- Consider if door lock should have safety interlocks (e.g., can't unlock during run)

### 2. Chilldown Mode + Auto-Start Recipe

The Controls section on Run page needs a "Chilldown" workflow:

**Proposed UI Layout:**
```
┌──────────────────────────────────────┐
│  [Chilldown] [✓ Start after chill]   │  <- Two small buttons/checkbox
├──────────────────────────────────────┤
│            [  START  ]               │  <- Full-width start button
└──────────────────────────────────────┘
```

**Behavior:**
- **Chilldown button**: Starts pre-cooling cycle (LN2 only, target temp)
- **"Start after chill" checkbox**: If checked, automatically starts recipe when chilldown complete
- Chilldown button only enabled when conditions met (LN2 controller online, no probe error)

**MCU-side:**
- Need `CMD_START_CHILLDOWN` command or integrate into state machine
- Chilldown state: `CHILLING` or similar
- Transition: `CHILLING` → `READY` when target temp reached
- If "auto-start" enabled: `READY` → `RUNNING` automatically
- Need to track chilldown target temp (from recipe or separate setting)

**State Machine Extension:**
```
IDLE ──► CHILLING ──► READY ──► RUNNING
              │          │
              └──────────┴──► (auto-start if enabled)
```

### 3. Relay Output Mapping

Need to define which relays control what:

| Relay | Function | Notes |
|-------|----------|-------|
| RO1 | ? | TBD |
| RO2 | ? | TBD |
| RO3 | Chamber light? | TBD |
| RO4 | Door lock? | TBD |
| RO5-8 | ? | TBD |

This mapping should be documented and consistent between MCU and app.

# Firmware Agent Handoff — Telemetry Extension + Lazy Polling Fix

Copy everything below the line into a new Claude conversation to hand off firmware work.

---

## Your Task

You are a firmware engineer working on an ESP32-S3 BLE GATT server for a cryogenic shaker ball mill control system. The Android app has implemented lazy polling controls and expects telemetry to include run state data. Your job is to:

1. **Extend telemetry packets** to include `wire_telemetry_run_state_t` (16 bytes)
2. **Verify lazy polling actually activates** after the configured idle timeout
3. **Persist idle timeout in NVS** so it survives MCU reboots

## Current Status (2025-01-20)

**WORKING:**
- ✅ SET_IDLE_TIMEOUT (0x0040) command accepted and stored
- ✅ GET_IDLE_TIMEOUT (0x0041) command returns stored value
- ✅ Telemetry streaming at 10Hz (header + controller data)
- ✅ Session management (OPEN_SESSION, KEEPALIVE)

**NOT WORKING — YOUR TASK:**
- ❌ **Telemetry doesn't include `wire_telemetry_run_state_t`** — App can't see lazy polling state
- ❌ **Lazy polling may not actually activate** — User set 1 min timeout, waited >1 min, still at 10Hz
- ❌ **Idle timeout not persisted in NVS** — MCU loses configured value on reboot

## Repository Location

```
/tmp/NuNuCryoShaker
```

(Clone from: https://github.com/jedwa006/NuNuCryoShaker.git)

---

## What You Need to Implement

### Priority 1: Extend Telemetry with Run State Data

The Android app expects telemetry packets to have this structure:

```
TELEMETRY_SNAPSHOT (msg_type=0x01):
| wire_telemetry_header_t (13 bytes) |
| controller_data (N × 10 bytes)     |
| wire_telemetry_run_state_t (16 bytes) |  ← ADD THIS
```

#### wire_telemetry_header_t (13 bytes) — Already Implemented
```c
typedef struct __attribute__((packed)) {
    uint32_t timestamp_ms;      // Uptime in ms
    uint16_t di_bits;           // Digital inputs (bits 0..7)
    uint16_t ro_bits;           // Relay outputs (bits 0..7)
    uint32_t alarm_bits;        // Alarm flags
    uint8_t  controller_count;  // Number of controller records following
} wire_telemetry_header_t;      // 13 bytes
```

#### controller_data (10 bytes each) — Already Implemented
```c
typedef struct __attribute__((packed)) {
    uint8_t  controller_id;     // 1-3
    int16_t  pv_x10;            // Process value × 10
    int16_t  sv_x10;            // Setpoint value × 10
    uint16_t op_x10;            // Output percent × 10
    uint8_t  mode;              // 0=STOP, 1=MANUAL, 2=AUTO, 3=PROGRAM
    uint16_t age_ms;            // Time since last Modbus poll
} wire_controller_data_t;       // 10 bytes
```

#### wire_telemetry_run_state_t (16 bytes) — ADD THIS
```c
typedef struct __attribute__((packed)) {
    uint8_t  machine_state;     // 0=IDLE, 1=READY, 2=RUNNING, 3=PAUSED, 4=E_STOP
    uint32_t run_elapsed_ms;    // Time since run started (0 if not running)
    uint32_t run_remaining_ms;  // Time remaining in run (0 if not running)
    int16_t  target_temp_x10;   // Target temperature × 10 (from recipe)
    uint8_t  recipe_step;       // Current recipe step (0 if not running)
    uint8_t  interlock_bits;    // Interlock status flags
    uint8_t  lazy_poll_active;  // 0=normal (10Hz), 1=lazy polling active
    uint8_t  idle_timeout_min;  // Configured idle timeout in minutes (0=disabled)
} wire_telemetry_run_state_t;   // 16 bytes total
```

**CRITICAL:** The `lazy_poll_active` and `idle_timeout_min` fields are what the app needs to display the current lazy polling state in Diagnostics.

### Priority 2: Verify Lazy Polling Activates

The firmware should:
1. Track time since last significant activity (run start, button press, app command)
2. After `idle_timeout_min` minutes of inactivity, set `lazy_poll_active = 1`
3. Reduce RS-485 polling frequency (e.g., from 10Hz to 1Hz)
4. Resume 10Hz polling when:
   - Any command is received from app
   - A run is started
   - Idle timeout is changed
   - System detects activity

**TEST:** Set idle timeout to 1 minute via app, wait >1 minute, observe:
- `lazy_poll_active` should become 1 in telemetry
- RS-485 polling should slow down (audible reduction in coil whine)

### Priority 3: Persist Idle Timeout in NVS (Critical)

**IMPORTANT:** The MCU must be the source of truth for idle timeout settings.

Currently, when the MCU reboots:
1. It loses the configured idle timeout value
2. App reconnects and queries GET_IDLE_TIMEOUT → gets 0
3. App displays "Disabled" even though user had previously enabled lazy polling
4. User has to reconfigure every time

**What you need to implement:**

1. **Store idle timeout in NVS** when SET_IDLE_TIMEOUT (0x0040) is received:
   ```c
   nvs_handle_t nvs;
   nvs_open("settings", NVS_READWRITE, &nvs);
   nvs_set_u8(nvs, "idle_timeout", timeout_minutes);
   nvs_commit(nvs);
   nvs_close(nvs);
   ```

2. **Load idle timeout from NVS** on boot:
   ```c
   nvs_handle_t nvs;
   uint8_t stored_timeout = 0;
   if (nvs_open("settings", NVS_READONLY, &nvs) == ESP_OK) {
       nvs_get_u8(nvs, "idle_timeout", &stored_timeout);
       nvs_close(nvs);
   }
   g_idle_timeout_minutes = stored_timeout;
   ```

3. **Report current value via telemetry** in `idle_timeout_min` field

4. **Return current value in GET_IDLE_TIMEOUT** response

**Design principle:** The app does NOT store the "true" idle timeout. It only:
- Sends SET_IDLE_TIMEOUT commands to change the MCU's value
- Queries GET_IDLE_TIMEOUT on connect to display the MCU's current value
- Reads telemetry to see if lazy polling is currently active

This ensures the MCU is always authoritative and the app never shows stale/conflicting state.

**TEST:**
1. Set idle timeout to 5 minutes via app
2. Power cycle the MCU
3. App reconnects
4. App should show "5 min" in Diagnostics (queried via GET_IDLE_TIMEOUT)

---

## Android App Reference

The app parses telemetry in:
```
/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/data/ble/WireProtocol.kt
```

```kotlin
// Parse extended run state if present (16 bytes)
var runState: RunStateData? = null
if (buffer.remaining() >= 16) {
    val machineState = buffer.get().toInt() and 0xFF
    val runElapsedMs = buffer.int.toLong() and 0xFFFFFFFFL
    val runRemainingMs = buffer.int.toLong() and 0xFFFFFFFFL
    val targetTempX10 = buffer.short
    val recipeStep = buffer.get().toInt() and 0xFF
    val interlockBits = buffer.get().toInt() and 0xFF
    val lazyPollActive = buffer.get().toInt() and 0xFF
    val idleTimeoutMin = buffer.get().toInt() and 0xFF

    runState = RunStateData(
        machineState = machineState,
        runElapsedMs = runElapsedMs,
        runRemainingMs = runRemainingMs,
        targetTempX10 = targetTempX10,
        recipeStep = recipeStep,
        interlockBits = interlockBits,
        lazyPollActive = lazyPollActive != 0,
        idleTimeoutMin = idleTimeoutMin
    )
}
```

The app currently shows a warning log every 5 seconds:
```
W BleMachineRepository: Telemetry: No run state data (firmware may not be sending extended telemetry)
```

This warning will disappear once you append the 16-byte run state to telemetry.

---

## Command Reference

### SET_IDLE_TIMEOUT (0x0040) — Already Implemented
```
Payload: timeout_minutes (u8)
  - 0 = disabled (always poll at 10Hz)
  - 1-255 = timeout in minutes

ACK: status=OK, detail=0
```

### GET_IDLE_TIMEOUT (0x0041) — Already Implemented
```
Payload: (none)

ACK: status=OK, optional_data = [timeout_minutes (u8)]
```

---

## Files to Modify

Based on the v0.3.3 firmware structure, you likely need to modify:

1. **Telemetry sending function** — Add `wire_telemetry_run_state_t` after controller data
2. **Idle tracking** — Implement a timer that tracks seconds since last activity
3. **Polling rate control** — Switch between 10Hz and slow rate based on idle state
4. **NVS storage** — Persist `idle_timeout_minutes` so it survives power cycles
5. **SET_IDLE_TIMEOUT handler** — Save value to NVS when received
6. **Initialization** — Load idle timeout from NVS on boot

---

## Acceptance Criteria

1. **Telemetry includes run state:**
   - App stops showing "No run state data" warning
   - Diagnostics → RS-485 Polling card shows idle timeout value from MCU

2. **Lazy polling activates:**
   - Set 1 min timeout via app Settings
   - Wait >1 min with no activity
   - `lazy_poll_active` becomes 1 in telemetry
   - Diagnostics shows "SLOW" chip and "Lazy (reduced rate)" mode

3. **Lazy polling resumes on activity:**
   - Any app command resets idle timer
   - `lazy_poll_active` returns to 0
   - Normal 10Hz polling resumes

4. **Idle timeout persists across MCU reboots:**
   - Set idle timeout to 5 minutes
   - Power cycle the MCU (not the app)
   - App reconnects and queries GET_IDLE_TIMEOUT
   - App shows "5 min" in Diagnostics (not "Disabled")
   - Lazy polling should still activate after 5 minutes of inactivity

---

## Testing with App

1. Build and flash firmware
2. Connect Android app (it auto-connects to last device)
3. Go to Diagnostics → RS-485 Polling card should show:
   - Polling mode: Normal (10 Hz) or Lazy (reduced rate)
   - Idle timeout: X min (or Disabled)
   - Status chip: FAST (green) or SLOW (yellow)

4. Go to Settings → Lazy Polling:
   - Toggle ON
   - Set to 1 minute
   - Wait >1 minute
   - Check Diagnostics — should show SLOW

---

## Relevant Documentation

| Document | Path |
|----------|------|
| Wire Protocol | `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/MCU_docs/30-wire-protocol.md` |
| Command Catalog | `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/MCU_docs/90-command-catalog.md` |
| App Agent Log | `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/AGENT_LOG.md` |
| App BLE Repository | `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/data/repository/BleMachineRepository.kt` |
| App Settings ViewModel | `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/ui/settings/SettingsViewModel.kt` |
| App Diagnostics Screen | `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/ui/diagnostics/DiagnosticsScreen.kt` |

---

## Quick Reference: Byte Layout

Telemetry packet total size with 3 controllers:
```
Header:           13 bytes
Controllers:      30 bytes (3 × 10)
Run State:        16 bytes
─────────────────────────
Total:            59 bytes
```

Run state byte offsets (from start of run state):
```
Offset  Size  Field
  0      1    machine_state
  1      4    run_elapsed_ms (little-endian)
  5      4    run_remaining_ms (little-endian)
  9      2    target_temp_x10 (little-endian)
 11      1    recipe_step
 12      1    interlock_bits
 13      1    lazy_poll_active    ← App reads this
 14      1    idle_timeout_min    ← App reads this
 15      1    (reserved/padding)
```
**Total: 16 bytes**

Good luck!

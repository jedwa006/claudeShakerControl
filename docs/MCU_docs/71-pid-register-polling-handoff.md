# PID Register Polling Architecture - Firmware Handoff

**Date**: 2026-01-20
**From**: App Agent (Android HMI)
**To**: Firmware Agent (ESP32-S3)
**Status**: Investigation findings and architecture proposal

---

## Reference Documentation (Absolute Paths)

| Document | Path |
|----------|------|
| **This Handoff** | `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/MCU_docs/71-pid-register-polling-handoff.md` |
| **I/O Logical Map** | `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/MCU_docs/70-io-logical-map.md` |
| **App Handoff from FW** | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/docs/APP_AGENT_HANDOFF.md` |
| **PID Controller Header** | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/pid_controller/include/pid_controller.h` |
| **PID Controller Impl** | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/pid_controller/pid_controller.c` |
| **Wire Protocol Spec** | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/docs/90-command-catalog.md` |

---

## Issue Summary

### Observed Behavior

1. **SET_MODE command succeeds** (ACK status=OK) but the physical E5CC controller displays "STOP" instead of the requested mode (AUTO/MANUAL)

2. **Mode not reflected in telemetry** - After SET_MODE, the telemetry continues to show the old mode value because MODE register (13) is not polled

3. **SV field shows "STOP"** on controller display when mode is changed, suggesting the controller may be in a standby state

### Root Cause Analysis

From `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/pid_controller/pid_controller.c`:

```c
/* Read registers 0-5 (PV, MV1, MV2, MVFB, STATUS, SV) in one request */
uint16_t regs[6];
modbus_err_t err = modbus_read_holding(ctrl->addr, 0, 6, regs);
```

The 10Hz telemetry loop only polls registers 0-5. **Register 13 (MODE) is never read back** after we write to it.

### LC108/E5CC Register Map (from pid_controller.h)

```c
#define LC108_REG_PV        0   /* Process Value, x10 */
#define LC108_REG_MV1       1   /* Output 1 %, x10 */
#define LC108_REG_MV2       2   /* Output 2 %, x10 */
#define LC108_REG_MVFB      3   /* Feedback %, x10 */
#define LC108_REG_STATUS    4   /* Status bitfield */
#define LC108_REG_SV        5   /* Setpoint, x10 */
#define LC108_REG_AT        12  /* Auto-tune (0=OFF, 1=ON) */
#define LC108_REG_MODE      13  /* Control mode */
#define LC108_REG_AL1       14  /* Alarm 1 setpoint, x10 */
#define LC108_REG_AL2       15  /* Alarm 2 setpoint, x10 */
#define LC108_REG_P1        24  /* P gain, x10 */
#define LC108_REG_I1        25  /* I time (seconds) */
#define LC108_REG_D1        26  /* D time (seconds) */
#define LC108_REG_LSPL      68  /* SV lower limit, x10 */
#define LC108_REG_USPL      69  /* SV upper limit, x10 */
```

---

## Proposed Register Polling Architecture

### Tier 1: Fast Telemetry (10Hz, always active)

These are the real-time values needed for live display:

| Register | Name | Priority | Notes |
|----------|------|----------|-------|
| 0 | PV | Critical | Process value display |
| 1 | MV1 | Critical | Output % display |
| 5 | SV | Critical | Setpoint display |
| 4 | STATUS | High | Fault/alarm bits |

**Current implementation polls 0-5**, which is correct for telemetry.

### Tier 2: Extended Telemetry (add to fast poll)

These should be included in every poll for accurate state display:

| Register | Name | Why |
|----------|------|-----|
| 13 | MODE | Must reflect actual controller mode |

**Proposal**: Expand fast poll to registers 0-5 AND 13 (7 registers total, still fits in single Modbus request)

### Tier 3: Slow Poll (~1Hz when IDLE, on-demand otherwise)

These change infrequently and don't need 10Hz updates:

| Register | Name | When to poll |
|----------|------|--------------|
| 12 | AT | After START_AUTOTUNE, until complete |
| 14-15 | AL1, AL2 | On screen entry, after SET_ALARM_LIMITS |
| 24-26 | P1, I1, D1 | On screen entry, after SET_PID_PARAMS |
| 68-69 | LSPL, USPL | On demand (settings screen) |

### Tier 4: On-Demand (explicit command from app)

For parameters that rarely change:

| Registers | Trigger |
|-----------|---------|
| 24-26 | READ_PID_PARAMS command |
| 14-15 | READ_ALARM_LIMITS command |
| 68-69 | New READ_SV_LIMITS command (proposed) |

---

## Implementation Tasks for Firmware

### Task 1: Add MODE to Fast Poll (High Priority)

Modify the telemetry polling in `pid_controller.c`:

```c
// BEFORE: Read registers 0-5
uint16_t regs[6];
modbus_err_t err = modbus_read_holding(ctrl->addr, 0, 6, regs);

// AFTER: Read registers 0-5 and 13
// Option A: Two reads (simpler)
uint16_t regs[6];
modbus_err_t err = modbus_read_holding(ctrl->addr, 0, 6, regs);
if (err == MODBUS_OK) {
    uint16_t mode_reg;
    err = modbus_read_holding(ctrl->addr, 13, 1, &mode_reg);
    ctrl->mode = mode_reg & 0xFF;
}

// Option B: Single read 0-13 (more efficient but larger)
uint16_t regs[14];
modbus_err_t err = modbus_read_holding(ctrl->addr, 0, 14, regs);
ctrl->mode = regs[13] & 0xFF;
```

### Task 2: Verify E5CC Mode Control Behavior

The E5CC may have specific requirements for mode changes. Check:

1. **RUN/STOP vs AUTO/MANUAL**: Some controllers separate "output enable" from "control mode"
   - There may be a separate RUN/STOP bit in STATUS register (4)
   - Writing MODE=2 (AUTO) may not start output if RUN is not set

2. **Register write verification**: After `pid_controller_set_mode()`, read back register 13 to confirm the write succeeded

3. **Mode change sequencing**: Some controllers require:
   - STOP → change mode → RUN
   - Or specific timing between writes

### Task 3: Read-After-Write Verification

After any SET command, poll the relevant register to confirm:

```c
esp_err_t pid_controller_set_mode(uint8_t addr, uint8_t mode)
{
    // ... existing write code ...

    // Add: Read back to verify
    uint16_t readback;
    if (modbus_read_holding(addr, LC108_REG_MODE, 1, &readback) == MODBUS_OK) {
        if ((readback & 0xFF) != mode) {
            ESP_LOGW(TAG, "Mode write verify failed: wrote %d, read %d",
                     mode, readback & 0xFF);
            return ESP_ERR_INVALID_RESPONSE;
        }
    }
    return ESP_OK;
}
```

### Task 4: Extended Register Poll Command (Optional)

Add a new command for bulk reading extended registers:

```
READ_EXTENDED_REGS (0x0029)
Payload: controller_id (u8)
ACK data: mode(u8), at_status(u8), al1_x10(i16), al2_x10(i16),
          p_x10(i16), i_time(u16), d_time(u16), sv_min(i16), sv_max(i16)
```

This allows the app to request all "slow" parameters in one command for settings screens.

---

## Timing Considerations

### Current Modbus Timing

- 3 controllers at ~300ms each = ~900ms round-trip
- 10Hz telemetry target = 100ms per frame
- **Conflict**: Can't poll all 3 controllers AND achieve 10Hz

### Proposed Strategy

| Machine State | Poll Behavior |
|---------------|---------------|
| IDLE | Full extended poll OK (~1 second cycle) |
| PRECOOL/RUNNING | Fast telemetry only (regs 0-5, 13) |
| After any SET_* | Force immediate poll of affected controller |

The app can use REQUEST_PV_SV_REFRESH (0x0022) to force an immediate poll after parameter changes.

---

## App-Side Changes (Already Implemented)

The Android app already:

1. ✅ Calls `requestPvSvRefresh()` after `setSetpoint()` for immediate UI update
2. ✅ Has placeholder methods for `readPidParams()`, `readAlarmLimits()`
3. ✅ Displays mode from telemetry (will show correct value once firmware reads it)

No app changes needed once firmware implements MODE polling.

---

## Test Checklist

After firmware changes:

- [ ] SET_MODE(AUTO) → telemetry shows mode=2
- [ ] SET_MODE(MANUAL) → telemetry shows mode=1
- [ ] SET_MODE(STOP) → telemetry shows mode=0
- [ ] Physical E5CC display matches telemetry mode
- [ ] Mode persists after command (doesn't revert to STOP)
- [ ] READ_PID_PARAMS returns actual P/I/D values
- [ ] READ_ALARM_LIMITS returns actual AL1/AL2 values

---

## Questions for Firmware Agent

1. **E5CC documentation**: Do you have the Omron E5CC Modbus register map? The LC108 registers may differ from E5CC.

2. **STATUS register (4) bits**: What do the bits in register 4 mean? Is there a RUN/STOP bit separate from MODE?

3. **Polling timing**: Is 900ms round-trip for 3 controllers acceptable? Can we optimize with bulk reads?

4. **Mode change behavior**: Have you tested mode changes on the physical controller? Does the display update?

---

## Revision History

| Date | Change |
|------|--------|
| 2026-01-20 | Initial handoff - MODE polling issue investigation |

# Modbus Register Editor - Firmware Handoff

**Date**: 2026-01-20
**From**: App Agent (Android HMI)
**To**: Firmware Agent (ESP32-S3)
**Status**: Feature request for generic register read/write commands

---

## Reference Documentation (Absolute Paths)

| Document | Path |
|----------|------|
| **This Handoff** | `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/MCU_docs/72-modbus-register-editor-handoff.md` |
| **LC108 Register Reference** | `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/lc108_register_reference.md` |
| **Wire Protocol Spec** | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/docs/90-command-catalog.md` |
| **PID Controller Impl** | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/pid_controller/pid_controller.c` |

---

## Feature Overview

The app needs to provide a **Modbus Register Editor** screen that allows technicians to read and modify any LC108/FT200 register from the wireless tablet, without opening the equipment enclosure.

**Use cases:**
- Adjust SV range limits (LSPL/USPL)
- Configure alarm setpoints and behavior
- Tune PID parameters (P, I, D gains)
- Change RS-485 address (commissioning)
- Start/stop auto-tune
- View diagnostic registers

The existing commands (SET_SV, SET_MODE, SET_PID_PARAMS, etc.) only cover a small subset of registers. We need generic read/write commands for full access.

---

## Requested Commands

### READ_REGISTERS (0x0030) - Read Arbitrary Registers

Read one or more consecutive holding registers from a PID controller.

**Payload:**
| Field | Type | Description |
|-------|------|-------------|
| controller_id | u8 | Controller address (1-3) |
| start_addr | u16 | Starting register address (little-endian) |
| count | u8 | Number of registers to read (1-16) |

**ACK Optional Data (on success):**
| Field | Type | Description |
|-------|------|-------------|
| controller_id | u8 | Echo of requested controller |
| start_addr | u16 | Echo of starting address |
| count | u8 | Number of registers returned |
| values | u16[] | Register values (little-endian, `count` entries) |

**Example:** Read registers 24-26 (P, I, D) from controller 2
```
cmd_id: 0x0030
payload: [0x02, 0x18, 0x00, 0x03]  // controller=2, addr=24, count=3
```

**ACK data:** `[0x02, 0x18, 0x00, 0x03, <P_lo>, <P_hi>, <I_lo>, <I_hi>, <D_lo>, <D_hi>]`

---

### WRITE_REGISTER (0x0031) - Write Single Register

Write a single holding register to a PID controller.

**Payload:**
| Field | Type | Description |
|-------|------|-------------|
| controller_id | u8 | Controller address (1-3) |
| reg_addr | u16 | Register address (little-endian) |
| value | u16 | Value to write (little-endian) |

**ACK:** Status code only (no optional data)

**Read-After-Write:** Firmware should read back the register after writing and return `INVALID_RESPONSE` if the value doesn't match.

**Example:** Set SV lower limit (reg 68) to -200.0°C on controller 1
```
cmd_id: 0x0031
payload: [0x01, 0x44, 0x00, 0x30, 0xF8]  // controller=1, addr=68, value=-2000
```

---

### WRITE_REGISTERS (0x0032) - Write Multiple Registers (Optional)

Write multiple consecutive registers in one command. This is a nice-to-have for batch updates.

**Payload:**
| Field | Type | Description |
|-------|------|-------------|
| controller_id | u8 | Controller address (1-3) |
| start_addr | u16 | Starting register address (little-endian) |
| count | u8 | Number of registers to write (1-8) |
| values | u16[] | Values to write (little-endian, `count` entries) |

**ACK:** Status code only

---

## Status Codes

Use existing status codes:

| Code | Name | When to use |
|------|------|-------------|
| 0x00 | OK | Success |
| 0x02 | INVALID_ARGS | Bad controller_id, count=0, count>16 |
| 0x04 | HW_FAULT | Modbus CRC error or framing issue |
| 0x06 | TIMEOUT | Controller didn't respond (detail=0x0004) |

For read-after-write mismatch, return `HW_FAULT` with a new detail code:
- `0x0006` = WRITE_VERIFY_FAILED

---

## Implementation Notes

### Modbus Function Codes

The LC108/FT200 uses standard Modbus RTU:
- **Read Holding Registers**: Function code 0x03
- **Write Single Register**: Function code 0x06
- **Write Multiple Registers**: Function code 0x10

The existing `modbus_read_holding()` and `modbus_write_holding()` functions should work.

### Register Address Mapping

LC108 register addresses in the documentation are already 0-based holding register addresses. No offset needed.

Example mappings:
| Register | Address | Description |
|----------|---------|-------------|
| PV | 0x0000 | Process value ×10 |
| SV | 0x0005 | Setpoint ×10 |
| MODE | 0x000D | Control mode |
| P1 | 0x0018 | Proportional gain ×10 |
| I1 | 0x0019 | Integral time (seconds) |
| D1 | 0x001A | Derivative time (seconds) |
| LSPL | 0x0044 | SV lower limit ×10 |
| USPL | 0x0045 | SV upper limit ×10 |

### Timing Considerations

These commands are **on-demand** (not part of the 10Hz fast loop). They can take 50-300ms for the Modbus round-trip, which is acceptable for configuration screens.

The app will show a loading indicator while waiting for the ACK.

---

## App-Side Implementation

The app will:

1. Define a register catalog with metadata (name, address, type, range, options)
2. Show registers organized by category
3. When user selects a register, send READ_REGISTERS to fetch current value
4. Display appropriate editor (slider, dropdown, number input)
5. Track staged changes locally
6. On "Write Changes", send WRITE_REGISTER for each modified register
7. Refresh affected registers after write to confirm

**Visibility:**
- Normal mode: Only common registers (SV limits, alarm setpoints)
- Service mode: Full register map with all categories

**Confirmation dialogs:**
- Normal mode: Confirm each individual write (prevent accidental changes)
- Service mode: Single confirmation for batch writes (user understands elevated permissions)

---

## Test Plan

After firmware implements the commands:

- [ ] READ_REGISTERS returns correct values for known registers (compare to physical display)
- [ ] READ_REGISTERS handles invalid controller_id (returns INVALID_ARGS)
- [ ] READ_REGISTERS handles controller offline (returns TIMEOUT)
- [ ] WRITE_REGISTER successfully changes SV limit (verify on physical display)
- [ ] WRITE_REGISTER read-after-write catches failures
- [ ] WRITE_REGISTER to read-only register fails appropriately
- [ ] Large count (>16) rejected with INVALID_ARGS

---

## Questions for Firmware Agent

1. **Read-only registers**: Does the LC108 have read-only registers that will reject writes? If so, do they return a Modbus exception or just ignore the write?

2. **Protected registers**: Are there any registers that require the controller to be in STOP mode before writing (like some communication settings)?

3. **Register count limit**: The Modbus spec allows up to 125 registers per read. I suggested 16 as a reasonable limit. Is there a constraint from the RS-485 buffer size?

4. **Timing**: What's the typical round-trip time for a Modbus read of 8 registers? This helps set appropriate timeout expectations in the app.

---

## Revision History

| Date | Change |
|------|--------|
| 2026-01-20 | Initial handoff - generic register read/write commands |
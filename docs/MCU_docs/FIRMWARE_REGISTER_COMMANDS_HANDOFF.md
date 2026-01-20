# Firmware Handoff: Generic Modbus Register Commands

**Date**: 2026-01-20
**App Version**: Current dev branch
**Purpose**: Enable Android app to read/write arbitrary Modbus registers on PID controllers

---

## Overview

The Android app now has a Register Editor screen that allows users to read and modify any Modbus register on the LC108/FT200 PID controllers. This requires two new BLE commands:

1. **READ_REGISTERS (0x0030)** - Read one or more consecutive registers
2. **WRITE_REGISTER (0x0031)** - Write a single register

These commands use the existing wire protocol framing and ACK mechanism.

---

## Command Specifications

### READ_REGISTERS (0x0030)

Read one or more consecutive Modbus holding registers from a PID controller.

**Request Payload (4 bytes):**
| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0 | 1 | controller_id | Modbus address (1, 2, or 3) |
| 1 | 2 | start_address | Starting register address (little-endian u16) |
| 3 | 1 | count | Number of registers to read (1-16) |

**ACK Payload on Success:**
| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0 | 1 | controller_id | Echo of requested controller |
| 1 | 2 | start_address | Echo of starting address (little-endian u16) |
| 3 | 1 | count | Number of registers returned |
| 4 | 2*count | values | Register values as little-endian u16 array |

**Example - Read SV register (address 4) from controller 2:**
```
Request:  cmd_id=0x0030, payload=[0x02, 0x04, 0x00, 0x01]
          controller_id=2, start_address=4, count=1

Response: status=OK, payload=[0x02, 0x04, 0x00, 0x01, 0x22, 0x01]
          controller_id=2, start_address=4, count=1, value=0x0122 (290 = 29.0°C)
```

**Error Cases:**
- `INVALID_ARGS` (0x02) with detail `PARAM_OUT_OF_RANGE` (0x0005): Invalid controller_id or count > 16
- `TIMEOUT_DOWNSTREAM` (0x06) with detail `CONTROLLER_OFFLINE` (0x0004): RS-485 timeout
- `NOT_READY` (0x05): PID module not initialized

---

### WRITE_REGISTER (0x0031)

Write a single Modbus holding register to a PID controller.

**Request Payload (5 bytes):**
| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0 | 1 | controller_id | Modbus address (1, 2, or 3) |
| 1 | 2 | address | Register address (little-endian u16) |
| 3 | 2 | value | Value to write (little-endian u16) |

**ACK Payload on Success:**
| Offset | Size | Field | Description |
|--------|------|-------|-------------|
| 0 | 1 | controller_id | Echo of controller |
| 1 | 2 | address | Echo of register address |
| 3 | 2 | value | Value that was written (verified by read-back) |

**Example - Write SV=30.0°C to controller 1:**
```
Request:  cmd_id=0x0031, payload=[0x01, 0x04, 0x00, 0x2C, 0x01]
          controller_id=1, address=4 (SV), value=0x012C (300 = 30.0°C)

Response: status=OK, payload=[0x01, 0x04, 0x00, 0x2C, 0x01]
```

**Error Cases:**
- `INVALID_ARGS` (0x02): Invalid controller_id or protected register
- `TIMEOUT_DOWNSTREAM` (0x06): RS-485 timeout
- `HW_FAULT` (0x04): Write succeeded but read-back verification failed

---

## Implementation Notes

### 1. Use Existing Modbus Infrastructure

The firmware already has `pid_controller.c` with functions like:
- `pid_read_register()` - Read a single register
- `pid_write_register()` - Write a single register

Extend or wrap these for the new commands.

### 2. Read-After-Write Verification

For WRITE_REGISTER, read back the register after writing to verify the value took effect. This catches cases where the controller rejects the value silently.

```c
esp_err_t result = pid_write_register(controller_id, address, value);
if (result == ESP_OK) {
    uint16_t readback;
    result = pid_read_register(controller_id, address, &readback);
    if (result == ESP_OK && readback != value) {
        return ESP_ERR_INVALID_RESPONSE;  // Verification failed
    }
}
```

### 3. Command Handler Registration

Add handlers in `ble_gatt.c`:

```c
case CMD_READ_REGISTERS:   // 0x0030
    handle_read_registers(payload, payload_len, ack_payload, ack_len);
    break;

case CMD_WRITE_REGISTER:   // 0x0031
    handle_write_register(payload, payload_len, ack_payload, ack_len);
    break;
```

### 4. Batch Read for Efficiency

READ_REGISTERS supports reading up to 16 consecutive registers in one command. Use Modbus function code 0x03 (Read Holding Registers) with the count parameter.

### 5. Protected Registers (Optional)

Consider rejecting writes to communication registers that could break connectivity:
- Address 49 (0x31): RS-485 address
- Address 50 (0x32): Baud rate
- Address 51 (0x33): Parity

Return `INVALID_ARGS` with detail `PARAM_OUT_OF_RANGE` if the app tries to write these.

---

## Testing Checklist

- [ ] READ_REGISTERS returns correct values for known registers (SV, PV, MODE)
- [ ] READ_REGISTERS with count > 1 returns consecutive register values
- [ ] READ_REGISTERS returns TIMEOUT for offline controller
- [ ] WRITE_REGISTER successfully changes SV
- [ ] WRITE_REGISTER read-back verification works
- [ ] WRITE_REGISTER returns error for invalid controller_id
- [ ] Both commands work while telemetry polling is active (no bus contention)

---

## Reference: Key LC108 Register Addresses

| Address | Name | Description |
|---------|------|-------------|
| 0 | PV | Process value × 10 |
| 1 | MV1 | Manipulated value 1 |
| 4 | SV | Setpoint × 10 |
| 5 | LSPL | Low setpoint limit |
| 6 | USPL | High setpoint limit |
| 13 | AM.RS | Control mode (0=PID, 1=Manual, 2=Stop) |
| 14 | P | Proportional gain × 10 |
| 15 | I | Integral time (seconds) |
| 16 | D | Derivative time (seconds) |

Full register map: See `/Users/joshuaedwards/Downloads/claudeShakerControl/docs/MCU_docs/72-modbus-register-editor-handoff.md`

---

## App-Side Implementation Status

The Android app already has:
- Command IDs defined in `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/data/ble/BleConstants.kt` (lines 73-75: 0x0030, 0x0031, 0x0032)
- Repository interface methods in `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/data/repository/MachineRepository.kt` (lines 104-120)
- BLE implementation in `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/data/repository/BleMachineRepository.kt`
- Register catalog in `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/domain/model/ModbusRegister.kt`
- ViewModel in `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/ui/registers/RegisterEditorViewModel.kt`
- UI Screen in `/Users/joshuaedwards/Downloads/claudeShakerControl/app/src/main/kotlin/com/shakercontrol/app/ui/registers/RegisterEditorScreen.kt`
- Deep link navigation: `shaker://registers/1`, `shaker://registers/2`, `shaker://registers/3`

Once firmware implements these commands, the Register Editor will be fully functional.

---

## Firmware Reference Paths

| Component | Absolute Path |
|-----------|---------------|
| PID Controller Header | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/pid_controller/include/pid_controller.h` |
| PID Controller Impl | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/pid_controller/pid_controller.c` |
| BLE GATT | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/ble_gatt/ble_gatt.c` |
| Wire Protocol Types | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/firmware/components/wire_protocol/include/wire_protocol.h` |
| Command Catalog Doc | `/Users/joshuaedwards/Documents/GitHub/NuNuCryoShaker/docs/90-command-catalog.md` |

# Firmware Agent Handoff Prompt

Copy everything below the line into a new Claude conversation to hand off firmware work.

---

## Your Task

You are a firmware engineer working on an ESP32-S3 BLE GATT server for a cryogenic shaker ball mill control system. The Android app is complete and tested. Your job is to implement command handlers so the app can control the hardware.

## Current Status (Updated 2025-01-16)

**IMPORTANT**: We have completed end-to-end testing with the Android app on a real tablet (Lenovo TB351FU). The following is WORKING:

✅ BLE advertising as "SYS-CTRL-D776"
✅ GATT connection and service discovery
✅ MTU negotiation (256 bytes)
✅ Telemetry streaming at 10Hz (TELEMETRY_SNAPSHOT, msg_type=0x01)
✅ Frame decoding with CRC validation
✅ Device Info characteristic (12 bytes)
✅ OPEN_SESSION command handling with session ID/lease ACK
✅ KEEPALIVE command handling

**NOT WORKING - YOUR TASK**:

❌ **SET_RELAY (0x0001) command is not being handled** (Priority 1)
❌ **SET_RELAY_MASK (0x0002) command is not being handled** (Priority 1)
❌ **SET_SV (0x0020) command is not being handled** (optional for v0)
❌ **SET_MODE (0x0021) command is not being handled** (optional for v0)

The Android app can now establish a session and maintain it with keepalives. However, when the user tries to control relay outputs from the I/O Control screen, the firmware returns what appears to be a rejection (visible as "NO ARGS" toast in the app). The app needs SET_RELAY and SET_RELAY_MASK implemented to control the machine.

**Why SET_RELAY_MASK?** The app uses SET_RELAY_MASK for atomic multi-relay operations needed for:
- Safety interlock states (multiple relays must change together)
- State machine automation (e.g., "motor enabled" requires several relays)
- Defined system states where relay combinations must be atomic

---

## Context

You are working on firmware for a cryogenic shaker ball mill control system. The **Android app is tested and working** on a real tablet. Your task is to implement command handlers for I/O control.

## Repository Location

```
/tmp/NuNuCryoShaker
```

(Clone from: https://github.com/jedwa006/NuNuCryoShaker.git)

## What You Need to Implement NOW

### Priority 1: SET_RELAY (0x0001)

The I/O Control screen in the Android app sends SET_RELAY commands to control relay outputs RO1-RO8.

#### Command Frame Structure

```
COMMAND frame (msg_type=0x10):
| proto_ver (u8) | msg_type=0x10 | seq (u16) | payload_len (u16) | payload | crc16 (u16) |

Payload for SET_RELAY:
| cmd_id=0x0001 (u16) | flags=0x0000 (u16) | relay_index (u8) | state (u8) |
```

- **relay_index**: 1-8 (channel number)
- **state**: 0=OFF, 1=ON, 2=TOGGLE

#### Expected Response

Send COMMAND_ACK (msg_type=0x11) on Events+Acks characteristic:

```
| proto_ver (u8) | msg_type=0x11 | seq (u16) | payload_len (u16) | payload | crc16 (u16) |

Payload:
| acked_seq (u16) | cmd_id=0x0001 (u16) | status (u8) | detail (u16) |
```

- **status**: 0=OK, 1=REJECTED_POLICY, 2=INVALID_ARGS
- **detail**: 0x0000=none, 0x0001=session invalid, 0x0002=interlock open

#### Example Frame (Hex)

Android app sends SET_RELAY CH3 ON:
```
01 10 XX XX 06 00 01 00 00 00 03 01 CC CC
│  │  │     │     │        │  │  └─ crc16
│  │  │     │     │        │  └─ state=ON
│  │  │     │     │        └─ relay_index=3
│  │  │     │     └─ cmd_id=0x0001, flags=0x0000
│  │  │     └─ payload_len=6
│  │  └─ seq
│  └─ msg_type=COMMAND
└─ proto_ver
```

Expected ACK:
```
01 11 YY YY 07 00 XX XX 01 00 00 00 00 CC CC
│  │  │     │     │     │     │  │     └─ crc16
│  │  │     │     │     │     │  └─ detail=none
│  │  │     │     │     │     └─ status=OK
│  │  │     │     │     └─ cmd_id=0x0001
│  │  │     │     └─ acked_seq (from command)
│  │  │     └─ payload_len=7
│  │  └─ seq (your counter)
│  └─ msg_type=COMMAND_ACK
└─ proto_ver
```

#### Implementation Notes

1. Parse relay_index and state from command payload
2. Validate relay_index is 1-8
3. Validate session is active (if session enforcement is enabled)
4. Control the actual GPIO/relay output
5. Update ro_bits in telemetry to reflect new state
6. Send ACK with status=OK or appropriate error

### Priority 1b: SET_RELAY_MASK (0x0002)

Atomically sets multiple relays in a single operation. Used for safety interlocks and state machine transitions where multiple relays must change together.

#### Command Frame Structure

```
COMMAND frame (msg_type=0x10):
| proto_ver (u8) | msg_type=0x10 | seq (u16) | payload_len (u16) | payload | crc16 (u16) |

Payload for SET_RELAY_MASK:
| cmd_id=0x0002 (u16) | flags=0x0000 (u16) | mask (u8) | values (u8) |
```

- **mask**: Bitmask of which channels to affect (bit 0 = RO1, ..., bit 7 = RO8)
- **values**: Target state for each channel (0=OFF, 1=ON for each bit)

Only channels with their mask bit set will be modified. Channels with mask bit 0 remain unchanged.

#### Example Frame (Hex)

Android app sends SET_RELAY_MASK to turn ON RO1+RO2, turn OFF RO3 (mask=0x07, values=0x03):
```
01 10 XX XX 06 00 02 00 00 00 07 03 CC CC
│  │  │     │     │        │  │  └─ crc16
│  │  │     │     │        │  └─ values=0x03 (RO1=ON, RO2=ON, RO3=OFF)
│  │  │     │     │        └─ mask=0x07 (affect RO1, RO2, RO3)
│  │  │     │     └─ cmd_id=0x0002, flags=0x0000
│  │  │     └─ payload_len=6
│  │  └─ seq
│  └─ msg_type=COMMAND
└─ proto_ver
```

Expected ACK (same format as SET_RELAY):
```
01 11 YY YY 07 00 XX XX 02 00 00 00 00 CC CC
│  │  │     │     │     │     │  │     └─ crc16
│  │  │     │     │     │     │  └─ detail=none
│  │  │     │     │     │     └─ status=OK
│  │  │     │     │     └─ cmd_id=0x0002
│  │  │     │     └─ acked_seq (from command)
│  │  │     └─ payload_len=7
│  │  └─ seq (your counter)
│  └─ msg_type=COMMAND_ACK
└─ proto_ver
```

#### Implementation Notes

1. Parse mask and values from command payload
2. Validate mask is non-zero (at least one channel affected)
3. Validate session is active (if session enforcement is enabled)
4. **Atomically** apply changes: for each bit position where mask bit is 1, set relay to corresponding values bit
5. Update ro_bits in telemetry to reflect new state
6. Send ACK with status=OK or appropriate error

#### Mask/Values Logic (pseudocode)

```c
// new_ro_bits = (current_ro_bits AND NOT mask) OR (values AND mask)
uint8_t new_ro_bits = (current_ro_bits & ~mask) | (values & mask);
```

### Priority 2 (Optional): SET_SV (0x0020) and SET_MODE (0x0021)

These control the PID temperature controllers. Lower priority for v0.

```
SET_SV payload: | controller_id (u8 1..3) | sv_x10 (i16) |
SET_MODE payload: | controller_id (u8 1..3) | mode (u8) |
```

Mode encoding:
- 0 = STOP
- 1 = MANUAL
- 2 = AUTO
- 3 = PROGRAM

---

## Testing Procedure

After implementing SET_RELAY:

```bash
# Build and flash firmware
./firmware/tools/idf main build
./firmware/tools/idf main flash

# Install Android app
cd /Users/joshuaedwards/Downloads/claudeShakerControl
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug

# Launch app
adb shell am start -n com.shakercontrol.app.debug/com.shakercontrol.app.MainActivity

# Navigate to I/O Control:
# 1. Swipe from left to open drawer
# 2. Tap "I/O Control"
# 3. Enable Service Mode from drawer (required for relay control)
# 4. Tap ON/OFF buttons on RO1-RO8

# Watch for command logs
adb logcat -s BleManager:D BleMachineRepository:D
```

### Success Criteria

1. Tap "ON" button for RO1 in app
2. See command sent in logcat
3. See ACK received with status=OK
4. Relay indicator in app shows ON state
5. Telemetry shows ro_bits updated

---

## Reference Documentation

Read these docs in the firmware repo:
- `docs/30-wire-protocol.md` - Frame format
- `docs/90-command-catalog.md` - Full command list (see section "I/O control")
- `docs/80-gatt-uuids.md` - UUID list

## Android App Code Reference

See how the app sends SET_RELAY and SET_RELAY_MASK:
- `BleMachineRepository.kt:829-858` - setRelay() function
- `BleMachineRepository.kt:860-887` - setRelayMask() function
- `WireProtocol.kt:210-211` - setRelay payload builder
- `WireProtocol.kt:213-221` - setRelayMask payload builder
- `BleConstants.kt:57-58` - SET_RELAY = 0x0001, SET_RELAY_MASK = 0x0002

---

## Commands for the Agent

```bash
# Clone repo (if needed)
cd /tmp && git clone https://github.com/jedwa006/NuNuCryoShaker.git

# Build main_app
./firmware/tools/idf main build

# Flash main_app
./firmware/tools/idf main flash

# Monitor serial output
./firmware/tools/idf main monitor
```

---

## Quick Start

1. Clone the firmware repo to `/tmp/NuNuCryoShaker`
2. Read `docs/90-command-catalog.md` to understand the command structure
3. Find where OPEN_SESSION and KEEPALIVE are handled (they work)
4. Add handlers for SET_RELAY (0x0001) and SET_RELAY_MASK (0x0002) following the same pattern
5. Build, flash, and test with the Android app

The key insight: commands arrive on the Command RX characteristic. You parse the frame, extract cmd_id from the payload, and dispatch to a handler. The handler does the work and sends an ACK on the Events+Acks characteristic.

Good luck!

# Firmware Agent Handoff Prompt

Use this prompt to spawn a parallel Claude agent for ESP32 firmware development.

---

## Current Status (Updated 2024-01-16)

**IMPORTANT**: We have completed end-to-end testing with the Android app on a real tablet (Lenovo TB351FU). The following is WORKING:

✅ BLE advertising as "SYS-CTRL-D776"
✅ GATT connection and service discovery
✅ MTU negotiation (256 bytes)
✅ Telemetry streaming at 10Hz (TELEMETRY_SNAPSHOT, msg_type=0x01)
✅ Frame decoding with CRC validation
✅ Device Info characteristic (12 bytes)

**NOT WORKING - YOUR TASK**:

❌ **OPEN_SESSION command is not being acknowledged**

The Android app sends OPEN_SESSION but receives no ACK:
```
>>> Opening session with nonce: -1298985094
<<< OPEN_SESSION ACK received: null, optionalData size=0
>>> Failed to open session: null, detail=null
```

The firmware needs to:
1. Listen for writes on the Command RX characteristic (`...5E63`)
2. Parse incoming COMMAND frames (msg_type=0x10)
3. Handle OPEN_SESSION (cmd_id=0x0100)
4. Send COMMAND_ACK (msg_type=0x11) on Events+Acks characteristic (`...5E64`)

---

## Context

You are working on firmware for a cryogenic shaker ball mill control system. The **Android app is tested and working** on a real tablet. Your task is to implement the command handler for OPEN_SESSION so the app can establish a session.

## Repository Location

```
/tmp/NuNuCryoShaker
```

(Clone from: https://github.com/jedwa006/NuNuCryoShaker.git)

## What Already Exists (DO NOT MODIFY)

The following are working and stable:

1. **Partition Table**: `firmware/partitions/partitions_16mb_recovery_ota.csv`
   - factory (recovery), ota_0, ota_1, storage (LittleFS)

2. **Recovery OTA Portal**: `firmware/apps/recovery_factory/`
   - SoftAP + HTTP OTA staging/activation
   - Keep this stable and untouched

3. **Main App Skeleton**: `firmware/apps/main_app/`
   - Boot button handler for recovery escape
   - OTA rollback support
   - This is where you'll add BLE

4. **Build System**: `firmware/tools/idf` wrapper
   - Use `./firmware/tools/idf main build` to build
   - Use `./firmware/tools/idf main flash` to flash

5. **sdkconfig defaults**: Already configured for ESP32-S3-WROOM-N16R8

## What You Need to Implement

Add BLE GATT server to `main_app` that communicates with the Android app.

### 1. GATT Service Structure

**Service UUID**: `F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E60`

| Characteristic | UUID | Properties | Direction |
|---|---|---|---|
| Device Info | `...5E61` | Read | ESP → App |
| Telemetry Stream | `...5E62` | Notify | ESP → App |
| Command RX | `...5E63` | Write, Write Without Response | App → ESP |
| Events + Acks | `...5E64` | Indicate, Notify | ESP → App |
| Bulk Gateway | `...5E65` | Write, Notify | Future |
| Diagnostic Log | `...5E66` | Notify | Optional |

Full UUIDs in `docs/80-gatt-uuids.md`.

### 2. Wire Protocol Frame Format

All frames use this header (little-endian):

```
| proto_ver (u8) | msg_type (u8) | seq (u16) | payload_len (u16) | payload (N bytes) | crc16 (u16) |
```

- **CRC**: CRC-16/CCITT-FALSE (poly 0x1021, init 0xFFFF)
- **proto_ver**: Currently 0x01

### 3. Message Types

#### 0x01 TELEMETRY_SNAPSHOT (Notify on Telemetry Stream)
```
timestamp_ms (u32)
di_bits (u16)
ro_bits (u16)
alarm_bits (u32)
controller_count (u8)
[controller_count times]:
  controller_id (u8)
  pv_x10 (i16)
  sv_x10 (i16)
  op_x10 (u16)
  mode (u8)
  age_ms (u16)
```

#### 0x10 COMMAND (Write on Command RX)
```
cmd_id (u16)
flags (u16)
cmd_payload (...)
```

Key commands to implement:
- `0x0100 OPEN_SESSION`: payload = `client_nonce (u32)`
- `0x0101 KEEPALIVE`: payload = `session_id (u32)`
- `0x0102 START_RUN`: payload = `session_id (u32), run_mode (u8)`
- `0x0103 STOP_RUN`: payload = `session_id (u32), stop_mode (u8)`

#### 0x11 COMMAND_ACK (Indicate/Notify on Events+Acks)
```
acked_seq (u16)
cmd_id (u16)
status (u8)        // 0=OK, 1=REJECTED_POLICY, 2=INVALID_ARGS, etc.
detail (u16)
optional_data (...)
```

For OPEN_SESSION ACK, include:
```
session_id (u32)
lease_ms (u16)     // e.g., 3000
```

### 4. Session Management

- Generate random session_id on OPEN_SESSION
- Default lease: 3000ms
- Require valid session_id for KEEPALIVE, START_RUN, STOP_RUN
- Track last keepalive time; if exceeded, session is stale

### 5. Advertising

- Device name: `SYS-CTRL-<shortid>` (e.g., last 4 hex digits of MAC)
- Include service UUID in advertising data

### 6. What's Already Working (Confirmed with Real Testing)

The following have been tested with the Android app on a Lenovo TB351FU tablet:

✅ BLE advertising with name "SYS-CTRL-D776"
✅ GATT service discovery
✅ MTU negotiation (tablet requests 512, ESP responds 256)
✅ Device Info characteristic returns 12 bytes
✅ Telemetry Stream sends TELEMETRY_SNAPSHOT every 100ms
✅ Frames are properly framed with CRC-16/CCITT-FALSE

### 7. What You Need to Implement NOW

**Priority: Command RX handler for OPEN_SESSION**

The Android app is sending OPEN_SESSION commands but getting no response. You need to:

1. **Set up write callback on Command RX characteristic** (`...5E63`)
   - Accept Write and Write Without Response

2. **Parse the incoming frame**:
   ```
   Received bytes (example): 01 10 XX XX 08 00 YY YY 00 00 ZZ ZZ ZZ ZZ CC CC

   - proto_ver: 01
   - msg_type: 10 (COMMAND)
   - seq: XX XX (little-endian u16)
   - payload_len: 08 00 = 8 bytes
   - payload:
     - cmd_id: YY YY (should be 00 01 for OPEN_SESSION)
     - flags: 00 00
     - client_nonce: ZZ ZZ ZZ ZZ (u32)
   - crc16: CC CC
   ```

3. **Generate and send COMMAND_ACK** on Events+Acks characteristic (`...5E64`):
   ```
   Frame structure:
   - proto_ver: 01
   - msg_type: 11 (COMMAND_ACK)
   - seq: (your response seq, incrementing)
   - payload_len: depends on content
   - payload:
     - acked_seq: (copy from received command's seq)
     - cmd_id: 00 01 (OPEN_SESSION)
     - status: 00 (OK)
     - detail: 00 00
     - optional_data:
       - session_id: (random u32)
       - lease_ms: B8 0B (3000 in little-endian)
   - crc16: (computed)
   ```

4. **Send via indication/notify** on `...5E64`

### 8. Example ACK Frame (Hex)

For a successful OPEN_SESSION ACK:
```
01                  // proto_ver
11                  // msg_type = COMMAND_ACK
XX XX               // seq (your seq counter)
0D 00               // payload_len = 13 bytes
YY YY               // acked_seq (from incoming command)
00 01               // cmd_id = OPEN_SESSION
00                  // status = OK
00 00               // detail
AA BB CC DD         // session_id (random u32)
B8 0B               // lease_ms = 3000
ZZ ZZ               // crc16
```

Total frame size: 8 (header) + 13 (payload) + 2 (crc) = 23 bytes

### 9. Testing Procedure

After implementing, the Android app will:
1. Connect to "SYS-CTRL-D776"
2. Immediately send OPEN_SESSION on Command RX
3. Wait up to 5 seconds for ACK on Events+Acks
4. If ACK received with status=OK, session is established
5. App will then start KEEPALIVE every ~2 seconds

Watch logcat on the tablet:
```bash
adb logcat -s BleManager:D BleMachineRepository:W
```

You should see:
```
>>> Opening session with nonce: XXXXXXXX
<<< OPEN_SESSION ACK received: OK, optionalData size=6
>>> Session opened: id=YYYYYYYY, lease=3000ms
```

### 10. Testing with Android App (Real Tablet Connected)

The Android app is at: `/Users/joshuaedwards/Downloads/claudeShakerControl`

**Test device**: Lenovo TB351FU tablet (Android 16, landscape 2000x1200)

After flashing firmware:
```bash
# Build and install app (if needed)
cd /Users/joshuaedwards/Downloads/claudeShakerControl
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew installDebug

# Launch the app
adb shell am start -n com.shakercontrol.app.debug/com.shakercontrol.app.MainActivity

# Navigate to Devices screen (tap on "Disconnected" status)
adb shell input tap 862 108

# Tap Reconnect button
adb shell input tap 1863 242

# Watch for session logs
adb logcat -s BleManager:D BleMachineRepository:W
```

Current state: App connects, receives telemetry, but OPEN_SESSION fails

### 8. Reference Documentation

Read these docs in the repo:
- `docs/20-gatt-schema.md` - GATT structure
- `docs/30-wire-protocol.md` - Frame format
- `docs/40-safety-heartbeat-and-policies.md` - Session/lease design
- `docs/80-gatt-uuids.md` - UUID list
- `docs/90-command-catalog.md` - Full command list

### 9. ESP-IDF BLE APIs

Use NimBLE stack (lighter than Bluedroid):
- `CONFIG_BT_NIMBLE_ENABLED=y`
- See ESP-IDF examples: `examples/bluetooth/nimble/bleprph`

Key includes:
```c
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"
```

### 11. Success Criteria

**Already passing** (confirmed with real tablet):
1. ✅ Android app discovers device by name "SYS-CTRL-D776"
2. ✅ App connects and discovers services
3. ✅ App receives TELEMETRY_SNAPSHOT notifications at 10Hz

**Your implementation is complete when**:
4. ⬜ App sends OPEN_SESSION and receives ACK with session_id
5. ⬜ App sends KEEPALIVE and receives ACK
6. ⬜ Session remains LIVE (no lease expiry warnings in app)

The key milestone is seeing this in logcat:
```
>>> Session opened: id=XXXXXXXX, lease=3000ms
```

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

Good luck!

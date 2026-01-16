# Firmware Agent Handoff Prompt

Use this prompt to spawn a parallel Claude agent for ESP32 firmware development.

---

## Context

You are working on firmware for a cryogenic shaker ball mill control system. The **Android app is complete** and waiting to test BLE communication. Your task is to implement the BLE GATT server and wire protocol in the ESP32-S3 firmware to establish communication with the Android app.

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

### 6. Minimum Viable Implementation

For initial handshake test, implement:

1. BLE advertising with correct name prefix
2. GATT service with all characteristics (can be stubs)
3. Device Info characteristic returns version bytes
4. Command RX accepts OPEN_SESSION, responds with ACK
5. Command RX accepts KEEPALIVE, responds with ACK
6. Telemetry Stream sends mock TELEMETRY_SNAPSHOT every 100ms

This will allow the Android app to:
- Discover the device
- Connect
- Open a session
- See telemetry data
- Maintain keepalive

### 7. Testing with Android App

The Android app is at: `/Users/joshuaedwards/Downloads/claudeShakerControl`

After flashing firmware:
1. Install app on tablet: `./gradlew installDebug`
2. Open app, go to Devices screen
3. Tap "Scan for devices"
4. Device should appear as "SYS-CTRL-xxxx"
5. Tap to connect
6. Watch logcat for connection flow

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

### 10. Success Criteria

The firmware is ready when:
1. Android app discovers device by name prefix `SYS-CTRL-`
2. App connects and discovers services
3. App sends OPEN_SESSION and receives ACK with session_id
4. App receives TELEMETRY_SNAPSHOT notifications
5. App sends KEEPALIVE and receives ACK
6. Session remains LIVE (no lease expiry warnings in app)

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

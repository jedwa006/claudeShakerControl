# UI Copy & Labels v1 — Cryogenic Shaker Mill HMI (Android 16 Tablet)

## 0) Tone and formatting rules

- Use concise, operator-friendly language.
- Prefer “Start / Pause / Resume / Stop” over synonyms.
- Avoid exclamation points and slang.
- Use sentence case (not Title Case) for body text.
- Use consistent time format: **mm:ss** for editable durations; show totals as **hh:mm:ss** when > 59 minutes.
- When showing “reason blocked,” use: **“Cannot <action>: <reason>.”**

---

## 1) Global top status strip

### 1.1 Screen titles
- Home
- Run
- Devices
- Alarms
- Diagnostics
- Settings
- PID 1
- PID 2
- PID 3
- Service mode (if implemented as a screen; otherwise as a mode banner)

### 1.2 Connection chip (states)
- Disconnected
- Scanning…
- Connecting…
- Connected
- Verified

Optional secondary text (small):
- RSSI: -58 dBm (only when connected)
- Updated 120 ms ago (only when verified and receiving data)

Tap action hint (optional tooltip or sublabel):
- Manage devices

### 1.3 MCU heartbeat chip
- MCU: OK
- MCU: stale (if last update exceeds threshold)
- MCU: missing (if never received)

Secondary text:
- Updated 120 ms ago
- Stale 3.2 s

### 1.4 Machine state chip
- Idle
- Ready
- Running
- Paused
- Fault
- E-stop

### 1.5 Alarm chip
- Alarms: 0
- Alarms: 1
- Alarms: 2 (1 high)

Tap action:
- View alarms

### 1.6 Service mode indicator
- Service: off
- Service: on

---

## 2) Home screen (cards + workflows)

### 2.1 Primary card: Run / live session
Title (dynamic):
- If Idle/Ready: **Run**
- If Running/Paused: **Live session**

Subtitle (dynamic examples):
- Not connected — connect to start
- Connected — verify to start
- Ready — configure and start
- Running — cycle 2 of 5 (milling)
- Paused — tap to resume

Primary button (dynamic):
- If not connected: **Connect**
- If connected but not verified: **Verify connection**
- If Idle/Ready: **Open run**
- If Running/Paused: **Resume live session**

Secondary text (optional, small):
- Recipe: 05:00 on / 01:00 hold × 5
- Est. total: 00:30:00

### 2.2 Temperatures card
Title: **Temperatures**
Rows:
- **PID 1 — Axle bearings**
- **PID 2 — Orbital bearings**
- **PID 3 — LN2 line**

Value labels:
- PV
- SV

Status labels (icons/LEDs):
- Enabled
- Output
- Fault

If data stale:
- Data stale (3.2 s)

Tap affordance:
- Tap a PID to open details

### 2.3 Status / interlocks card
Title: **Status**
Recommended rows (v1):
- E-stop
- Door locked
- LN2 present
- Power enabled
- Heaters enabled
- Motor enabled

If unknown/unavailable:
- Not available

### 2.4 Diagnostics card
Title: **Diagnostics**
Body:
- View connection health, inputs, capabilities, and firmware info.
Button: **Open diagnostics**

### 2.5 Settings card
Title: **Settings**
Body:
- App settings and export.
Button: **Open settings**

---

## 3) Run screen (cockpit)

### 3.1 Section headers
- Recipe
- Controls
- Temperatures
- Indicators
- Manual controls

### 3.2 Recipe editor (interval cycle controller)

Field labels:
- Milling (on)
- Hold (off)
- Cycles

Helper labels:
- Milling total
- Hold total
- Estimated total

Time input placeholders:
- mm:ss

Cycle input placeholder:
- 1

Derived total formatting:
- 00:25:00 (hh:mm:ss)

Running status block (when active):
- Cycle 2 of 5
- Phase: Milling
- Phase remaining: 03:12
- Total remaining: 00:18:37

### 3.3 Control buttons (large)

States and labels:
- **Start**
- **Pause**
- **Resume**
- **Stop**

Optional secondary small text (beneath controls):
- Commands are confirmed by the controller.

Disabled-state reasons (examples):
- Cannot start: not connected.
- Cannot start: connection not verified.
- Cannot start: required capability missing (PID 2 comms).
- Cannot pause: machine is not running.
- Cannot resume: machine is not paused.

### 3.4 Stop confirmation dialog

Title: **Stop run?**
Body:
- This will stop the current run. The controller will transition to a safe state.
Buttons:
- Cancel
- Stop

Optional checkbox (later):
- Do not ask again (not recommended for safety actions)

### 3.5 PID tiles (on Run screen)

Titles:
- PID 1 — Axle bearings
- PID 2 — Orbital bearings
- PID 3 — LN2 line

Value labels:
- PV
- SV (omit SV if PID3 is read-only and SV not meaningful)

If PID capability = 0:
- Not installed

If PID capability = 1:
- Optional (warning icon)

If PID capability = 2 and missing/fault:
- Required — unavailable

Tap:
- Open PID details

### 3.6 Indicator bank

Group title: **Indicators**
Column/row labels (examples; adapt to your IO map):
- Door
- LN2
- E-stop
- Power
- Heater 1
- Heater 2
- Motor
- Fault

Heartbeat sub-section title: **Health**
Rows:
- BLE link
- MCU heartbeat

States:
- OK
- Stale (3.2 s)
- Missing

### 3.7 Manual controls (normal mode)

Title: **Manual controls**
Controls (examples; can be placeholders):
- Lights
- Door lock
- Vent (if applicable)
- Reset fault (if safe and supported)

Switch labels:
- On / Off (or use standard switch without text)

If action requires confirmation:
- Confirm (see section 6)

---

## 4) Devices screen (BLE)

Title: **Devices**

### 4.1 Sections
- Known device
- Available devices

Known device card:
- Last connected: <device name>
Buttons:
- Connect
- Forget

Available devices list empty state:
- No devices found.
Body:
- Make sure the controller is powered and in range.

Scan control:
- Scan
- Stop scan

Connection states in list:
- Tap to connect
- Connecting…
- Connected

### 4.2 Permission prompts (Android 16)

Permission panel title:
- Permission required
Body (choose based on missing permission):
- Bluetooth permission is required to scan and connect.
- Location services may be required on some devices for BLE scanning.

Buttons:
- Grant permission
- Open system settings (only if required)

---

## 5) Alarms

### 5.1 Alarm banner (global)
- No active alarms
- 1 active alarm
- 2 active alarms (1 high)

If high severity:
Title:
- High priority alarm
Body:
- <alarm text from MCU>

Buttons:
- View alarms
- Acknowledge (if enabled)

### 5.2 Alarm list page
Title: **Alarms**
Tabs (optional):
- Active
- History

Row fields:
- Severity: High / Medium / Low
- Message: <text>
- Time: 14:32:10
- State: Active / Cleared
- Acknowledged: Yes/No

Empty state:
- No alarms.

Acknowledge button (if implemented):
- Acknowledge

---

## 6) Confirmations and warnings (standard text)

### 6.1 Service mode entry
Dialog title: **Enter service mode?**
Body:
- Service mode exposes manual overrides intended for qualified users. The controller remains responsible for safety.
Buttons:
- Cancel
- Enter

Service mode banner (persistent when enabled):
- Service mode is on. Manual overrides are visible.

Service mode exit prompt (optional):
Title: **Exit service mode?**
Buttons:
- Cancel
- Exit

### 6.2 Risky manual action confirmation (template)
Title: **Confirm action**
Body:
- Proceed with “<action>”?
Buttons:
- Cancel
- Confirm

### 6.3 Connection loss during run
Banner:
- Connection lost. The controller will continue if safe.
Buttons:
- Reconnect
- View status

### 6.4 Data stale
Inline label:
- Data stale (3.2 s)

---

## 7) Diagnostics page copy

Title: **Diagnostics**

Section titles:
- Connection
- Heartbeats
- Capabilities
- Firmware
- Inputs

Field labels:
- RSSI
- Last update age
- BLE link age
- MCU heartbeat age
- Firmware version
- Build ID

Capabilities table columns:
- Subsystem
- Level
- Status

Capability level labels:
- Not installed (0)
- Optional (1)
- Required (2)
- Simulated (3)

---

## 8) Settings page copy

Title: **Settings**

Rows:
- Theme
  - Dark (default)
  - System
- Time input format
  - mm:ss
- Export logs
  - Export

About section:
- App version
- Controller version (if available)

---

## 9) Error message patterns

- Use short, actionable messages:
  - Cannot connect: permission denied.
  - Cannot connect: device not found.
  - Command failed: controller rejected request.
  - Cannot start: PID 2 comms missing (required).
  - Cannot start: e-stop is active.

Prefer showing a single primary reason; avoid stacking paragraphs.

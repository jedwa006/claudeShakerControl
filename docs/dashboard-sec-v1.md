# Dashboard Spec v1 — Cryogenic Shaker Mill HMI (Android 16 Tablet)

## 0. Goals

- **Dummy-proof** home experience: simple, guided actions for non-expert users.
- **Professional** run control: high-signal cockpit for active operation.
- **MCU authoritative**: app is a UI client; it does not own safety logic.
- **Fast iteration**: modular screens, reusable components, minimal ambiguity for agents.

Target device: Lenovo Tab Plus (11.5", 2000×1200), landscape-first.

---

## 1. Global UI Principles

### 1.1 Information hierarchy
- **Always visible (global chrome):** connection status, machine state, alarm summary, service-mode indicator.
- **Home:** “what do I do now?” cards; minimal controls.
- **Run:** full cockpit with big controls + live data.
- **Detail pages:** deeper per-subsystem views (PIDs, diagnostics).

### 1.2 Color semantics (strict)
Use neutral UI by default. Color is reserved for meaning:
- **Normal/Ready:** neutral + subtle accent
- **Active/Running:** active accent (non-alarming)
- **Warning:** amber/yellow (non-blocking)
- **Alarm/Fault:** red (blocking or urgent)
- **Disconnected/Stale:** gray + “stale age” text

### 1.3 Touch + legibility
- Minimum touch target: 48dp.
- Primary operational values (PV, countdown): large and high contrast.
- Avoid dense text; prefer labels + values.

### 1.4 Motion
- No decorative animation.
- Allowed: subtle pulsing for “output active” and an attention draw for new alarms.

---

## 2. Screen Map (v1)

1) **Home (Cards)**
2) **Run (Cockpit)**
3) **Devices (BLE Scan/Connect)**
4) **PID Summary List** (optional; may be embedded as Home card)
5) **PID Detail (PID1 / PID2 / PID3)**
6) **Alarms**
7) **Diagnostics**
8) **Settings**
9) **Service Mode** (not necessarily separate screen; can be a mode overlay + page)

---

## 3. Global Chrome: Persistent Top Status Strip

Visible on **all screens**.

### 3.1 Left
- Hamburger menu icon
- Screen title (Home / Run / Devices / etc.)

### 3.2 Center (chips)
- **Connection chip:** DISCONNECTED / SCANNING / CONNECTING / CONNECTED / VERIFIED  
  - Optional subtext: RSSI (if connected)  
- **MCU heartbeat chip:** “MCU: OK” + last update age (e.g., `120 ms`)  
  - If stale: show `STALE 3.2s`
- **Machine state chip:** IDLE / READY / RUNNING / PAUSED / FAULT / E-STOP

### 3.3 Right
- **Alarm chip:** `Alarms: 0` or `Alarms: 2 (1 High)`; color by highest severity  
- **Service mode indicator:** OFF/ON (when ON, show a persistent warning banner below strip)

### 3.4 Behavior
- Tapping Connection chip opens **Devices** screen.
- Tapping Alarm chip opens **Alarms** screen.
- If machine is RUNNING/PAUSED and app opens/reconnects, default navigation to **Run** (or show a dominant “Resume Live Session” banner on Home that auto-opens Run after X seconds).

---

## 4. Home Screen (Dummy-proof Cards)

### 4.1 Card layout (top → bottom)
1) **Primary Card: Run / Live Session**
   - If IDLE/READY: shows “Start Run”
   - If RUNNING/PAUSED: shows “Resume Live Session” (dominant)
   - Displays summary:
     - Current recipe (ON/HOLD/Cycles) if set
     - Estimated total time
     - If running: current phase + time remaining + cycle index

2) **Temperatures Card**
   - Shows 3 compact PID summaries:
     - PID1 (Axle Bearings): PV, SV, status LEDs
     - PID2 (Orbital Bearings): PV, SV, status LEDs
     - PID3 (LN2 Line): PV (SV optional), status LEDs
   - Tap anywhere on a PID row → PID detail page.

3) **Status / Interlocks Card**
   - Compact list of key booleans (initially placeholders if needed):
     - E-STOP
     - Door locked
     - LN2 present
     - Contactor power enabled
     - Heaters enabled
     - Motor enabled
   - Show capability-related warnings inline (see section 8).

4) **Diagnostics Card**
   - Short description + “Open Diagnostics”

5) **Settings Card**
   - “Open Settings”

### 4.2 Home actions
- Primary action button goes to **Run** screen (never starts high-power actions unless verified + allowed).
- If not VERIFIED: “Start Run” button should instead prompt to connect/verify.

---

## 5. Run Screen (Professional Cockpit)

This is the operator control center during operation.

### 5.1 Sections

A) **Recipe / Interval Cycle Controller (primary)**
- Inputs:
  - Mill ON duration (mm:ss)
  - Hold duration (mm:ss)
  - Cycle count (int)
- Derived values:
  - Total milling time = ON * cycles
  - Total holding time = HOLD * cycles (or HOLD*(cycles-1) depending your semantics; define explicitly)
  - Total runtime = milling + holding
- Live state (when running):
  - Current cycle (e.g., `3 / 5`)
  - Phase (MILLING / HOLDING)
  - Phase countdown
  - Total remaining time

B) **Big Controls (consistent placement)**
- Start (only when READY/IDLE)
- Pause (only when RUNNING)
- Resume (only when PAUSED)
- Stop / Abort (when RUNNING/PAUSED; requires confirmation)
- Button states must reflect MCU-reported machine state.

C) **PID Tiles (3)**
Each tile shows:
- Title: PID1 / PID2 / PID3 + friendly name
- PV (large), SV (smaller)
- LEDs: Enabled, Output active (pulsing allowed), Fault
- Tap → PID detail page.

D) **Indicator Bank**
- Group 1: Digital inputs/system status (list or grid of LEDs)
- Group 2 (separated): Heartbeats
  - BLE comms heartbeat
  - MCU heartbeat
- Show “stale age” when data stops updating.

E) **Manual Controls**
- Normal mode: essential toggles (lights, door lock, etc.) + any safe manual actions.
- Service mode: additional overrides appear, visually segregated with warning stripe and labels.

### 5.2 Confirmation rules
- STOP/ABORT: confirmation dialog with clear consequence text.
- Service overrides: confirmation and/or “hold to activate” pattern for risky actions.
- Avoid repeated confirmations for benign actions (e.g., lights).

---

## 6. Devices Screen (BLE)

### 6.1 Must support
- Scan list with device name + RSSI + “connect”
- Last-known device section with one-tap reconnect
- Disconnect
- Forget device
- Connection state explanation + common troubleshooting hints

### 6.2 Permission UX
- If permissions missing, show a dedicated permission panel with a single “Grant” action and a short explanation.

---

## 7. PID Detail Pages (v1 placeholders)

Separate pages: PID1, PID2, PID3
- Title, summary PV/SV, status
- Placeholder sections for:
  - Setpoint control
  - Mode control
  - Modbus register map (later)
- For v1: no deep register writes required; the page should exist and be navigable.

---

## 8. Capability Bits: UI Behavior (Required)

Capability values:  
0 = not present  
1 = expected but not required (warning allowed)  
2 = required (block)  
3 = simulated (future)

### 8.1 Presentation layer
- Capability=0: hide the control group OR show “Not installed” (pick one global policy; default: show row but disabled with “Not installed”).
- Capability=1: show with warning icon and “Optional” label.
- Capability=2: show normally, but if missing/faulted -> blocking banner and disable dependent controls.

### 8.2 Gating layer (UI-level)
- “Arm / Start” controls disabled unless:
  - Connection is VERIFIED
  - Required capabilities satisfied
  - MCU reports READY
- When disabled, show a single-line reason:
  - “Cannot start: PID2 comms missing (required).”

### 8.3 Development layer (future)
- Capability=3 reserved for “simulated”; UI should label simulated sources clearly.

---

## 9. Alarms (v1 minimal but structured)

### 9.1 Alarm banner
- Appears in top strip via alarm chip.
- If severity high: optional persistent banner under strip until acknowledged.

### 9.2 Alarm list page
Each alarm row includes:
- Severity
- Title
- Timestamp (from MCU if possible)
- State: Active / Cleared / Acknowledged
- Acknowledge action (role-gated later; for v1 may exist but disabled)

---

## 10. Diagnostics Screen (v1)

Must show:
- RSSI (if connected)
- Last update age + heartbeat ages
- Firmware version / build ID (if available)
- Capability bits table (subsystem → value)
- Raw key boolean states (door, estop, LN2 present, etc.)

---

## 11. Settings Screen (v1)

Minimal:
- Theme (dark default; allow system)
- Units/time input behavior (mm:ss)
- Export logs (if flight recorder implemented)
- About (app version)

---

## 12. Service Mode (v1)

### 12.1 Entry
- Only via hamburger menu → “Service Mode”
- Show warning dialog: “Service mode exposes overrides intended for qualified users.”

### 12.2 Visual treatment
- When ON: persistent warning banner across top of content area.
- Service-only controls are grouped and labeled “SERVICE”.

### 12.3 Behavior
- Service mode affects visibility/enabling of manual overrides only.
- It must never override MCU safety. If MCU rejects, UI must show clear feedback.

---

## 13. Component Inventory (for agents)

Reusable components to implement early:
- StatusStrip (top chrome)
- StateChip (generic)
- LedIndicator (on/off/stale)
- ValueTile (label + big value + unit + status)
- RecipeCard (editable + derived totals)
- ControlBar (Start/Pause/Resume/Stop)
- AlarmChip + AlarmListRow
- CapabilityBadge (0/1/2/3)

---

## 14. Definition of Done for Dashboard v1 (MVP UI)

- Home screen implemented with cards and correct gating behavior.
- Run screen implemented with recipe controls, derived totals, and stateful controls.
- Top status strip visible everywhere.
- Devices screen exists with scan/connect skeleton (mock acceptable initially).
- PID pages exist and are navigable.
- Diagnostics and Alarms pages exist and display mock data.
- Capability bits appear in UI and block Start/Arm when required conditions not satisfied.

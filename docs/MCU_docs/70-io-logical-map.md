# I/O Logical Map — Cryogenic Shaker Ball Mill

This document provides the logical mapping between physical I/O channels and their system functions, plus the state machine requirements for MCU-driven control.

Related docs:
- Hardware: `docs/MCU_docs/07-hardware-controller-waveshare-esp32-s3-eth-8di-8ro.md`
- System inventory: `docs/system-inventory.md`
- Command catalog: `docs/MCU_docs/90-command-catalog.md`
- State machines: `docs/MCU_docs/96-state-machine.md`

---

## 1. Digital Inputs (DI1–DI8)

| Channel | Signal Name | Active State | Purpose | Interlock? |
|---------|-------------|--------------|---------|------------|
| DI1 | ESTOP | LOW = E-Stop active | Emergency stop | Yes - immediate halt |
| DI2 | DOOR_CLOSED | HIGH = Closed | Door position switch | Yes - blocks start, halts run |
| DI3 | LN2_PRESENT | HIGH = Present | LN2 supply available | Yes - warning, may block cooling |
| DI4 | MOTOR_FAULT | HIGH = Fault | VFD fault signal | Yes - halts run |
| DI5 | RESERVED | — | Future use | — |
| DI6 | RESERVED | — | Future use | — |
| DI7 | RESERVED | — | Future use | — |
| DI8 | RESERVED | — | Future use | — |

### Interlock Logic (MCU-enforced)

```
INTERLOCKS_OK = (DI1 == HIGH) &&   // E-Stop not pressed
                (DI2 == HIGH)       // Door closed

START_ALLOWED = INTERLOCKS_OK &&
                HMI_LIVE &&
                (MACHINE_STATE == IDLE)

COOLING_ALLOWED = START_ALLOWED &&
                  (DI3 == HIGH)     // LN2 present (warning if not)
```

---

## 2. Relay Outputs (RO1–RO8 / CH1–CH8)

| Channel | Signal Name | Function | Auto-Control | Service Mode |
|---------|-------------|----------|--------------|--------------|
| CH1 | MAIN_CONTACTOR | Motor power enable | State machine | Manual override |
| CH2 | HEATER_1 | Axle bearing heater | PID1 demand | Manual override |
| CH3 | HEATER_2 | Orbital bearing heater | PID2 demand | Manual override |
| CH4 | LN2_VALVE | Liquid nitrogen solenoid | State machine + PID3 | Manual override |
| CH5 | DOOR_LOCK | Solenoid locking bar | State machine | Manual override |
| CH6 | CHAMBER_LIGHT | Chamber lighting | User request | Manual override |
| CH7 | RESERVED | Future use | — | Manual override |
| CH8 | RESERVED | Future use | — | Manual override |

### Relay State Matrix by Machine State

| State | CH1 Motor | CH2 HTR1 | CH3 HTR2 | CH4 LN2 | CH5 Lock | CH6 Light |
|-------|-----------|----------|----------|---------|----------|-----------|
| IDLE | OFF | OFF | OFF | OFF | OFF | User |
| PRECOOL | OFF | PID | PID | ON/PID | ON | User |
| RUNNING | ON | PID | PID | ON/PID | ON | User |
| STOPPING | OFF | OFF | OFF | OFF | ON* | User |
| FAULT | OFF | OFF | OFF | OFF | OFF** | User |
| SERVICE | Manual | Manual | Manual | Manual | Manual | Manual |

Notes:
- `*` Lock held for thermal soak period
- `**` Lock released for operator access during fault investigation

---

## 3. PID Controllers (RS-485)

| ID | Name | Purpose | SV Editable | Heater Relay |
|----|------|---------|-------------|--------------|
| 1 | PID_LN2_COLD | LN2 line cold temp monitor | No (read-only) | None |
| 2 | PID_AXLE | Axle bearing temperature | Yes | CH2 (HEATER_1) |
| 3 | PID_ORBITAL | Orbital bearing temperature | Yes | CH3 (HEATER_2) |

### PID → Relay Mapping

The MCU translates PID output percentage to relay duty cycle using pulse-width modulation (if supported) or simple on/off hysteresis:

```
HEATER_1 (CH2) state = PID2.output > 0 && MACHINE_STATE in [PRECOOL, RUNNING]
HEATER_2 (CH3) state = PID3.output > 0 && MACHINE_STATE in [PRECOOL, RUNNING]
LN2_VALVE (CH4) state = (MACHINE_STATE in [PRECOOL, RUNNING]) && (TARGET_TEMP < CURRENT_TEMP)
```

---

## 4. Machine State Machine (MCU-Resident)

### States

```
IDLE        → Machine ready, outputs safe
PRECOOL     → Cooling chamber before motor start
RUNNING     → Motor active, temperature controlled
STOPPING    → Controlled shutdown sequence
E_STOP      → Emergency stopped, outputs latched safe
FAULT       → Hardware fault detected, outputs safe
SERVICE     → Manual control mode (app-controlled outputs)
```

### State Transitions

```
IDLE → PRECOOL
  Trigger: START_RUN command (run_mode=NORMAL or PRECOOL_ONLY)
  Guard: INTERLOCKS_OK && HMI_LIVE
  Action: Lock door (CH5), start cooling (CH4)

PRECOOL → RUNNING
  Trigger: Temperature targets reached OR timeout
  Guard: INTERLOCKS_OK && HMI_LIVE
  Action: Enable motor (CH1)

RUNNING → STOPPING
  Trigger: STOP_RUN command OR recipe complete OR HMI_STALE timeout
  Action: Disable motor (CH1), continue cooling for soak

STOPPING → IDLE
  Trigger: Temperature safe OR timeout
  Action: Unlock door (CH5), disable all outputs

Any → E_STOP
  Trigger: DI1 goes LOW (E-stop pressed)
  Action: IMMEDIATE disable all outputs, latch state

E_STOP → IDLE
  Trigger: E-stop released (DI1 HIGH) AND CLEAR_ESTOP command
  Guard: HMI_LIVE (acknowledge required)

Any → FAULT
  Trigger: DI4 HIGH (motor fault) OR PID probe error OR RS-485 timeout
  Action: Disable outputs, emit EVENT

FAULT → IDLE
  Trigger: CLEAR_FAULT command
  Guard: Fault condition resolved

IDLE ↔ SERVICE
  Trigger: ENABLE_SERVICE_MODE / DISABLE_SERVICE_MODE command
  Guard: MACHINE_STATE == IDLE
```

---

## 5. Command Requirements for HMI ↔ MCU

### Commands App → MCU

| cmd_id | Name | Payload | Purpose |
|--------|------|---------|---------|
| 0x0100 | OPEN_SESSION | nonce(u32) | Start HMI session |
| 0x0101 | KEEPALIVE | session_id(u32) | Maintain lease |
| 0x0102 | START_RUN | session_id, run_mode | Begin process |
| 0x0103 | STOP_RUN | session_id, stop_mode | End process |
| 0x0001 | SET_RELAY | relay_index, state | Manual relay control (service mode) |
| 0x0020 | SET_SV | controller_id, sv_x10 | Set PID setpoint |
| 0x0021 | SET_MODE | controller_id, mode | Set PID mode |
| 0x0110 | ENABLE_SERVICE_MODE | session_id | Enter service mode |
| 0x0111 | DISABLE_SERVICE_MODE | session_id | Exit service mode |
| 0x00F1 | CLEAR_WARNINGS | — | Clear warning flags |
| 0x00F2 | CLEAR_LATCHED_ALARMS | — | Clear fault state |

### Status Updates MCU → App (in telemetry)

The telemetry snapshot already includes:
- `di_bits` (DI1–DI8 state)
- `ro_bits` (CH1–CH8 state)
- `alarm_bits` (interlock/fault flags)
- PID controller readings

**Proposed additions for run state:**

| Field | Type | Description |
|-------|------|-------------|
| machine_state | u8 | Current state enum (IDLE=0, PRECOOL=1, RUNNING=2, etc.) |
| run_elapsed_ms | u32 | Time since run started (0 if not running) |
| run_remaining_ms | u32 | Time until run completes (0 if no target) |
| target_temp_x10 | i16 | Current target temperature |
| recipe_step | u8 | Current recipe step (0-based) |
| interlock_bits | u8 | Which interlocks are blocking start |

### Events MCU → App

| event_id | Name | When |
|----------|------|------|
| 0x1200 | RUN_STARTED | IDLE → PRECOOL or RUNNING |
| 0x1201 | RUN_STOPPED | → IDLE (normal completion) |
| 0x1202 | RUN_ABORTED | → IDLE or FAULT (abnormal) |
| 0x1203 | PRECOOL_COMPLETE | PRECOOL → RUNNING |
| 0x1204 | STATE_CHANGED | Any state transition |

---

## 6. Safety Policy Summary

### MCU-Enforced (Cannot be overridden by app)

1. **E-Stop**: DI1 LOW immediately disables all outputs
2. **Door Interlock**: DI2 LOW prevents start, halts running motor
3. **Motor Fault**: DI4 HIGH triggers fault state
4. **HMI Timeout**: If lease expires during run, MCU continues to safe stop
5. **Service Mode Guards**: Manual relay control only in SERVICE state

### App-Enforced (Soft guards)

1. **Recipe Validation**: Validate temperature/time limits before sending
2. **Start Confirmation**: Require user confirmation for potentially dangerous operations
3. **PID Setpoint Limits**: Validate SV values within safe range

---

## 7. HMI Display Labels

Update IoScreen.kt to show these labels instead of generic "DI1", "RO1":

### Digital Inputs
| Channel | Short Label | Full Label |
|---------|-------------|------------|
| DI1 | E-Stop | Emergency Stop |
| DI2 | Door | Door Closed |
| DI3 | LN2 | LN2 Present |
| DI4 | VFD | Motor Fault |
| DI5 | — | Reserved |
| DI6 | — | Reserved |
| DI7 | — | Reserved |
| DI8 | — | Reserved |

### Relay Outputs
| Channel | Short Label | Full Label |
|---------|-------------|------------|
| CH1 | Motor | Main Contactor |
| CH2 | HTR1 | Heater 1 (Axle) |
| CH3 | HTR2 | Heater 2 (Orbital) |
| CH4 | LN2 | LN2 Valve |
| CH5 | Lock | Door Lock |
| CH6 | Light | Chamber Light |
| CH7 | — | Reserved |
| CH8 | — | Reserved |

---

## 8. Implementation Priority

### Phase 1: Basic State Machine
- [ ] Implement state enum in firmware
- [ ] Add `machine_state` to telemetry
- [ ] Enforce interlock checks for START_RUN
- [ ] E-stop immediate halt

### Phase 2: Run Sequence
- [ ] PRECOOL → RUNNING transition
- [ ] Temperature-based gating
- [ ] Run timer/elapsed tracking
- [ ] Clean STOP_RUN sequence

### Phase 3: PID Integration
- [ ] Relay control from PID output
- [ ] Setpoint validation
- [ ] Probe error detection → FAULT

### Phase 4: Service Mode
- [ ] SERVICE state entry/exit
- [ ] Manual relay control in service mode
- [ ] Return-to-IDLE guards

---

## Revision History

| Date | Change |
|------|--------|
| 2026-01-19 | Initial I/O logical map from Stage 8 planning |

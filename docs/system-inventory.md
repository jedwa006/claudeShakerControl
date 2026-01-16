# System Inventory — Cryogenic Shaker Ball Mill

This document maintains a registry of all sensors, peripherals, I/O devices, and their associated capability bits for the HMI project.

---

## 1. Controller Hardware

### Main Controller
| Item | Value |
|------|-------|
| Board | Waveshare ESP32-S3-POE-ETH-8DI-8RO |
| MCU | ESP32-S3-WROOM-1U-N16R8 |
| Flash | 16 MB |
| PSRAM | 8 MB Octal SPI |
| Framework | ESP-IDF v5.5.2 |

### Onboard I/O
| Resource | Count | Notes |
|----------|-------|-------|
| Digital Inputs (DI) | 8 | DI1–DI8, optoisolated |
| Relay Outputs (RO) | 8 | CH1–CH8, via TCA9554PWR I/O expander |
| RS-485 Port | 1 | UART on GPIO17/18 |
| Ethernet | 1 | W5500 via SPI (not used for HMI) |
| SD Card | 1 | Recipe storage |

---

## 2. Capability Bits Registry

Capability bit values:
- `0` = Not equipped / not present
- `1` = Expected but not required (warning; system can operate)
- `2` = Expected and required (missing/fault prevents operation)
- `3` = Simulated (future)

### Subsystem Capability Bits

| Bit | Subsystem | Type | Description |
|-----|-----------|------|-------------|
| 0 | PID_1 | Temperature Controller | Axle bearings PID (LC108 or equivalent) |
| 1 | PID_2 | Temperature Controller | Orbital bearings PID |
| 2 | PID_3 | Temperature Controller | LN2 line PID |
| 3 | LN2_VALVE | Solenoid Valve | Liquid nitrogen supply valve |
| 4 | DOOR_ACTUATOR | Solenoid | Door locking bar (solenoid type) |
| 5 | DOOR_SWITCH | Sensor | Door position sensor (simple switch; may change to inductive) |
| 6 | RELAY_INTERNAL | Relay Bank | Onboard 8-channel relays (CH1–CH8) |
| 7 | RELAY_EXTERNAL | Relay Bank | External RS-485 relay bank (8-ch board or interposer relays) |
| 8–31 | Reserved | — | Future expansion |

### Feature Capability Bits (from Device Info)

| Bit | Feature | Description |
|-----|---------|-------------|
| 0 | SUPPORTS_SESSION_LEASE | Heartbeat/lease protocol supported |
| 1 | SUPPORTS_EVENT_LOG | Event logging available |
| 2 | SUPPORTS_BULK_GATEWAY | Bulk gateway characteristic active |
| 3 | SUPPORTS_MODBUS_TOOLS | Modbus register tools available |
| 4 | SUPPORTS_PID_TUNING | PID parameter editing available |
| 5 | SUPPORTS_OTA | Over-the-air updates (future) |

---

## 3. Temperature Controllers (PID)

### PID 1 — Axle Bearings
| Property | Value |
|----------|-------|
| Controller ID | 1 |
| Model | LC108 (or equivalent) |
| Communication | RS-485 Modbus RTU |
| Address | TBD |
| Purpose | Monitor/control axle bearing temperature |

### PID 2 — Orbital Bearings
| Property | Value |
|----------|-------|
| Controller ID | 2 |
| Model | LC108 (or equivalent) |
| Communication | RS-485 Modbus RTU |
| Address | TBD |
| Purpose | Monitor/control orbital bearing temperature |

### PID 3 — LN2 Line
| Property | Value |
|----------|-------|
| Controller ID | 3 |
| Model | LC108 (or equivalent) |
| Communication | RS-485 Modbus RTU |
| Address | TBD |
| Purpose | Monitor LN2 supply line temperature |
| Note | May be read-only (no SV control needed) |

---

## 4. Actuators and Valves

### LN2 Valve
| Property | Value |
|----------|-------|
| Type | Solenoid valve |
| Control | Via relay output |
| Purpose | Control liquid nitrogen flow |
| Capability Bit | 3 |

### Door Actuator
| Property | Value |
|----------|-------|
| Type | Solenoid locking bar |
| Control | Via relay output |
| Purpose | Lock chamber door during operation |
| Capability Bit | 4 |

---

## 5. Sensors

### Door Switch
| Property | Value |
|----------|-------|
| Type | Simple switch (potential upgrade to inductive) |
| Input | Digital input (DI) |
| Purpose | Detect door open/closed state |
| Capability Bit | 5 |
| Safety | Interlock — door must be closed to run |

### E-Stop
| Property | Value |
|----------|-------|
| Type | Physical emergency stop button |
| Input | Hardwired to DI |
| Purpose | Emergency shutdown |
| Safety | Hardware-first; MCU monitors state |

---

## 6. Relay Banks

### Internal Relays (Onboard)
| Property | Value |
|----------|-------|
| Channels | CH1–CH8 |
| Control | I2C via TCA9554PWR |
| Rating | 10A @ 250VAC / 10A @ 30VDC |
| Capability Bit | 6 |

### External Relay Bank (Optional)
| Property | Value |
|----------|-------|
| Options | 8-ch RS-485 relay board OR RS-485 interposer relays |
| Communication | RS-485 |
| Purpose | Additional relay capacity |
| Capability Bit | 7 |

---

## 7. Digital Input Assignments (Preliminary)

| DI | Assignment | Notes |
|----|------------|-------|
| DI1 | E-Stop | Emergency stop state |
| DI2 | Door Switch | Door open/closed |
| DI3 | LN2 Present | LN2 supply available |
| DI4 | TBD | |
| DI5 | TBD | |
| DI6 | TBD | |
| DI7 | TBD | |
| DI8 | TBD | |

---

## 8. Relay Output Assignments (Preliminary)

| CH | Assignment | Notes |
|----|------------|-------|
| CH1 | Main Contactor | Motor power |
| CH2 | Heater 1 | TBD |
| CH3 | Heater 2 | TBD |
| CH4 | LN2 Valve | Liquid nitrogen |
| CH5 | Door Lock | Solenoid actuator |
| CH6 | Lights | Chamber lighting |
| CH7 | TBD | |
| CH8 | TBD | |

---

## 9. Communication Interfaces

### BLE (Primary HMI Link)
| Property | Value |
|----------|-------|
| Role | ESP32 = Peripheral/Server; Tablet = Central/Client |
| Service UUID | `F0C5B4D2-3D1E-4A27-9B8A-2F0B3C4D5E60` |
| Telemetry Rate | 10 Hz baseline |
| Heartbeat | 1 Hz keepalive |

### RS-485 (PID Controllers)
| Property | Value |
|----------|-------|
| Baud Rate | 9600 |
| Format | 8N1 |
| Protocol | Modbus RTU |
| Devices | Up to 3 PID controllers |

---

## 10. Future Expansion Notes

- **QR Code Recipe Import:** Desktop app generates QR; tablet scans via camera
- **External Relay Banks:** RS-485 addressable for additional outputs
- **Inductive Door Sensor:** Potential upgrade from simple switch
- **OTA Updates:** Reserved capability bit for future implementation

---

## Revision History

| Date | Change |
|------|--------|
| 2026-01-15 | Initial inventory from Stage 0 planning |

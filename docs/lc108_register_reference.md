# LC108-802 / FT200 / MF108 — Modbus Register Reference
This reference compiles the Modbus holding-register map used by the LC108-802 family (firmware-compatible with Maxwell FT200/MF108). Temperature and percent values are scaled ×10 (e.g., `253` → `25.3 °C`).
**Protocol:** RS‑485 Modbus‑RTU (Function Codes 03/06/10). **Typical defaults:** 9600 bps, 8‑N‑1.

## Sources
- FT200 communication protocol (register list)
- FT200 user manual (menu terminology)

## Fast‑poll Registers (UI live loop)
| addr_hex | name | unit | scale | type | rw | range | tooltip |
|---:|---|---|---:|---|---|---|---|
| `0000` | PV | °C | 0.1 | S16 | R | -1999.9..9999.9 | Process value. Decimal point per dP (0042h). |
| `0001` | MV1 | % | 0.1 | U16 | R | 0..100.0 | Output1 (OP1) PID indication (duty). |
| `0002` | MV2 | % | 0.1 | U16 | R | 0..100.0 | Output2 PID indication (if present). |
| `0004` | OutputLEDs | bitmask | 1 | U16 | R | 0..65535 | Panel indicators bitfield: b0 COM, b1 MAN, b2 AL3, b3 AL2, b4 AL1, b5 AT, b6 OUT2, b7 OUT1, b8 PRG, b9 SV4, b10 SV3, b11 SV2, b12 SV1, b13 C, b14 F, b15 %. |
| `0005` | SV | °C | 0.1 | S16 | R/W | -1999.9..9999.9 | Set value (target). |

## Configuration & Control Registers (on‑demand)
> **Write guidance:** Use FC06 for single-register writes; FC16 when batching (e.g., P/I/D). Clamp to the `range`. When switching to **MANUAL** (`000Dh=1`), immediately set **ManualOutput** (`002Ah`).

| addr_hex | name | unit | scale | type | rw | range | tooltip | notes |
|---:|---|---|---:|---|---|---|---|---|
| `0006` | S.F00 | nan | nan | ENUM | R/W | nan | Shortcut for SV (panel/menu placement). | nan |
| `0007` | SV1 | °C | 0.1 | S16 | R/W | -1999.9..9999.9 | Event input SV1. | nan |
| `0008` | SV2 | °C | 0.1 | S16 | R/W | -1999.9..9999.9 | Event input SV2. | nan |
| `0009` | SV3 | °C | 0.1 | S16 | R/W | -1999.9..9999.9 | Event input SV3. | nan |
| `000A` | SV4 | °C | 0.1 | S16 | R/W | -1999.9..9999.9 | Event input SV4. | nan |
| `000B` | S.F01 | nan | nan | ENUM | R/W | nan | Shortcut for F01 group. | nan |
| `000C` | AT | nan | nan | ENUM | R/W | nan | Auto-tune: 0=OFF,1=ON. | Writing '1' starts Auto-Tune; controller clears back to '0' when finished. Writing '1' starts Auto-Tune; controller clears back to '0' when finished. |
| `000D` | AM.RS (ControlMode) | nan | nan | ENUM | R/W | nan | Operating mode: 0=PID,1=MANUAL,2=STOP,5=END. | 0=PID (AUTO), 1=MANUAL, 2=STOP, 5=END. When switching to MANUAL, write ManualOutput (002A) immediately. 0=PID (AUTO), 1=MANUAL, 2=STOP, 5=END. When switching to MANUAL, write ManualOutput (002A) immediately. |
| `000E` | AL1_Value | °C | 0.1 | S16 | R/W | -1999.9..9999.9 | Alarm 1 threshold (mode via 004Ch). | Alarm 1 threshold; behavior mode set by ALd1 (004C). Alarm 1 threshold; behavior mode set by ALd1 (004C). |
| `000F` | AL2_Value | °C | 0.1 | S16 | R/W | -1999.9..9999.9 | Alarm 2 threshold (mode via 004Fh). | Alarm 2 threshold; behavior mode set by ALd2 (004F). Alarm 2 threshold; behavior mode set by ALd2 (004F). |
| `0010` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0011` | UAd (DeviceAddress) | nan | nan | U16 | R | 0..255 | Node ID (see 005Eh for writable). | nan |
| `0012` | S.F02 | nan | nan | ENUM | R/W | nan | Shortcut for F02 group. | nan |
| `0013` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0014` | RAMP | °C/min | 0.1 | U16 | R/W | 0.0..999.9 | SV ramp-up rate. | nan |
| `0015` | T1 (SoakTime) | s\|min | 1 | U16 | R/W | 0..9999 | Soak time; unit via 0071h. | nan |
| `0016` | S.F03 | nan | nan | ENUM | R/W | nan | Shortcut for F03 group. | nan |
| `0017` | SC (InputOffset) | °C | 0.1 | S16 | R/W | -199.9..999.9 | Bias applied to PV. | nan |
| `0018` | P1 | °C | 0.1 | U16 | R/W | 0.0..800.0 | Proportional band (group 1). | Tuning tip: change P in small increments; re-check stability before adjusting I/D. Tuning tip: change P in small increments; re-check stability before adjusting I/D. |
| `0019` | I1 | s | 1 | U16 | R/W | 0..3600 | Integral time (group 1). | I=0 disables integral action on some firmware. I=0 disables integral action on some firmware. |
| `001A` | D1 | s | 1 | U16 | R/W | 0..3600 | Derivative time (group 1). | D=0 disables derivative action. D=0 disables derivative action. |
| `001B` | TimerDisplay | nan | nan | U16 | R | 0..9999 | Built-in timer (display). | nan |
| `001C` | ATVL | °C | 0.1 | S16 | R/W | nan | Auto-tuning reset windup. | nan |
| `001D` | CYT1 | s | 1 | U16 | R/W | 0..100 | Cycle time for group 1 (SSR period). | Cycle time sets SSR period; too small increases wear, too large reduces control resolution. Cycle time sets SSR period; too small increases wear, too large reduces control resolution. |
| `001E` | HYS1 | °C | 0.1 | U16 | R/W | 0.1..900.0 | On/Off hysteresis (group 1). | nan |
| `001F` | rSt1 | °C | 0.1 | S16 | R/W | -199.9..199.9 | Reset bias (group 1). | nan |
| `0020` | OPL1 | % | 0.1 | U16 | R/W | 0..100.0 | OP1 lower limit. | Lower output clamp; set >0 to avoid heater dead zone. Lower output clamp; set >0 to avoid heater dead zone. |
| `0021` | OPH1 | % | 0.1 | U16 | R/W | 0..100.0 | OP1 upper limit. | Upper output clamp; set <100% to cap heater power. Upper output clamp; set <100% to cap heater power. |
| `0022` | bUF1 | % | 0.1 | U16 | R/W | 0..100.0 | Soft-start buffer (group 1). | nan |
| `0023` | PKo1 | % | 0.1 | U16 | R/W | 0..100.0 | Manual output default after power-on. | nan |
| `0024` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0025` | OLAP | °C | 0.1 | U16 | R/W | 0.0..100.0 | Overlap area for heat+cool. | nan |
| `0026` | GAP2 | °C | 0.1 | U16 | R/W | 0.0..200.0 | Cooling-side SV offset. | nan |
| `0027` | P2 | °C | 0.1 | U16 | R/W | 0.0..800.0 | Proportional band (group 2). | nan |
| `0028` | I2 | s | 1 | U16 | R/W | 0..3600 | Integral time (group 2). | nan |
| `0029` | D2 | s | 1 | U16 | R/W | 0..3600 | Derivative time (group 2). | nan |
| `002A` | ManualOutput | % | 0.1 | U16 | R/W | 0..100.0 | Manual duty when mode=MANUAL. | Manual output duty (0–100.0%). Only used when Mode=MANUAL (000D=1). Manual output duty (0–100.0%). Only used when Mode=MANUAL (000D=1). |
| `002B` | CYT2 | s | 1 | U16 | R/W | 0..100 | Cycle time (group 2). | nan |
| `002C` | HYS2 | °C | 0.1 | U16 | R/W | 0.1..900.0 | Hysteresis (group 2). | nan |
| `002D` | rSt2 | °C | 0.1 | S16 | R/W | -199.9..199.9 | Reset bias (group 2). | nan |
| `002E` | OPL2 | % | 0.1 | U16 | R/W | 0..100.0 | OP2 lower limit. | nan |
| `002F` | OPH2 | % | 0.1 | U16 | R/W | 0..100.0 | OP2 upper limit. | nan |
| `0030` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0031` | S.F04 | nan | nan | ENUM | R/W | nan | Shortcut for F04 group. | nan |
| `0032` | SFSV | °C | 0.1 | S16 | R/W | -199.9..3275.0 | Soft-start target SV. | Soft-start target SV; enable via SFST (006B). Soft-start target SV; enable via SFST (006B). |
| `0033` | STME | min | 1 | U16 | R/W | 0..100 | Soft-start effective time. | Soft-start duration in minutes. Soft-start duration in minutes. |
| `0034` | SOUT | % | 0.1 | U16 | R/W | 0..100.0 | Soft-start output cap. | Soft-start output cap in percent. Soft-start output cap in percent. |
| `0035` | S.F05 | nan | nan | ENUM | R/W | nan | Shortcut for F05 group. | nan |
| `0036` | LBAt | s | 1 | U16 | R/W | 0..9999 | Loop-break alarm check time. | nan |
| `0037` | LBAB | °C | 0.1 | U16 | R/W | nan | Loop-break alarm threshold. | nan |
| `0038` | HBAt | s | 1 | U16 | R/W | 0..9999 | Heater short-circuit check time. | nan |
| `0039` | HBAB | °C | 0.1 | U16 | R/W | nan | Heater short-circuit threshold. | nan |
| `003A` | S.F06 | nan | nan | ENUM | R/W | nan | Shortcut for F06 group. | nan |
| `003B` | 1LR | nan | nan | ENUM | R/W | nan | Alarm 1 latch (0=no,1=yes). | nan |
| `003C` | 2LR | nan | nan | ENUM | R/W | nan | Alarm 2 latch (0=no,1=yes). | nan |
| `003D` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `003E` | S.F07 | nan | nan | ENUM | R/W | nan | Shortcut for F07 group. | nan |
| `003F` | LCK (AccessLevel) | nan | nan | U16 | R/W | 0..8 | Access protection level. | nan |
| `0040` | S.F08 | nan | nan | ENUM | R/W | nan | Shortcut for F08 group. | nan |
| `0041` | INP1 (InputType) | nan | nan | ENUM | R/W | nan | 0=K,1=E,2=J,3=N,4=WRe3/25,5=S,6=T,7=R,8=B,9=AN1,10=AN2,13=Pt100 | Input type: 0=K,1=E,2=J,3=N,4=WRe3/25,5=S,6=T,7=R,8=B,9=AN1,10=AN2,13=Pt100. Input type: 0=K,1=E,2=J,3=N,4=WRe3/25,5=S,6=T,7=R,8=B,9=AN1,10=AN2,13=Pt100. |
| `0042` | dP | nan | nan | ENUM | R/W | nan | Display decimal point / analog format. | Decimal point for display; also affects scaling for PV/SV via Temp display. Decimal point for display; also affects scaling for PV/SV via Temp display. |
| `0043` | Unit | nan | nan | ENUM | R/W | nan | 0=°C,1=°F,2=none | nan |
| `0044` | LSPL | °C | 0.1 | S16 | R/W | nan | SV lower limit. | nan |
| `0045` | USPL | °C | 0.1 | S16 | R/W | nan | SV upper limit. | nan |
| `0046` | PVoS | °C | 0.1 | S16 | R/W | nan | Input offset (same as SC). | nan |
| `0047` | PVFt | nan | nan | U16 | R/W | 0..60 | PV input filter strength. | nan |
| `0048` | ANL1 | nan | nan | S16 | R/W | nan | Analog input lower display limit. | nan |
| `0049` | ANH1 | nan | nan | S16 | R/W | nan | Analog input upper display limit. | nan |
| `004A` | tRSL | °C | 0.1 | S16 | R/W | nan | Re-transmission lower limit. | nan |
| `004B` | tRSH | °C | 0.1 | S16 | R/W | nan | Re-transmission upper limit. | nan |
| `004C` | ALd1 | nan | nan | U16 | R/W | 0..23 | Alarm 1 mode. | nan |
| `004D` | AH1 | °C | 0.1 | U16 | R/W | nan | Alarm 1 hysteresis. | nan |
| `004E` | ALt1 | s | 1 | U16 | R/W | 0..9999 | Alarm 1 output delay. | nan |
| `004F` | ALd2 | nan | nan | U16 | R/W | 0..23 | Alarm 2 mode. | nan |
| `0050` | AH2 | °C | 0.1 | U16 | R/W | nan | Alarm 2 hysteresis. | nan |
| `0051` | ALt2 | s | 1 | U16 | R/W | 0..9999 | Alarm 2 output delay. | nan |
| `0052` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0053` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0054` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0055` | Oud1 (HeatCoolAction) | nan | nan | ENUM | R/W | nan | 0=Reverse(heat),1=Direct(cool) | nan |
| `0056` | bER1 (AnalogSoftStart) | nan | nan | ENUM | R/W | nan | 0=None,1=Always,2=When analog rising | nan |
| `0057` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0058` | RUCY | s | 1 | U16 | R/W | 0..200 | Motor travel time (valve/positioner). | nan |
| `0059` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `005A` | Reserved/PMd hdr | nan | nan | nan | R | nan | Header for program mode. | nan |
| `005B` | PMd (ProgramMode) | nan | nan | ENUM | R/W | nan | 0=Standard,1=Temp hold,2=Ramp up | nan |
| `005C` | tSP | °C | 0.1 | U16 | R/W | nan | Timer kick-in temperature. | nan |
| `005D` | PEND | nan | nan | ENUM | R/W | nan | 0=END at timer finish,1=PID continues | nan |
| `005E` | Idno (Address) | nan | nan | U16 | R/W | 0..255 | Device address (writable). | Writable device address. Change with caution on a live bus. Writable device address. Change with caution on a live bus. |
| `005F` | bAUd | nan | nan | ENUM | R/W | nan | 0=2400,1=4800,2=9600,3=19200 | Baud rate: 0=2400,1=4800,2=9600,3=19200. Baud rate: 0=2400,1=4800,2=9600,3=19200. |
| `0060` | Ucr (Parity) | nan | nan | ENUM | R/W | nan | 0=8N1,1=8O1,2=8E1 | Parity/stop: 0=8N1, 1=8O1, 2=8E1. Parity/stop: 0=8N1, 1=8O1, 2=8E1. |
| `0061` | EXC1 | nan | nan | ENUM | R/W | nan | Alarm 1 relay logic. | nan |
| `0062` | A1L1 | nan | nan | ENUM | R/W | nan | Alarm 1 latch: 0=no,1=yes. | nan |
| `0063` | EXC2 | nan | nan | ENUM | R/W | nan | Alarm 2 relay logic. | nan |
| `0064` | A1L2 | nan | nan | ENUM | R/W | nan | Alarm 2 latch: 0=no,1=yes. | nan |
| `0065` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0066` | Reserved | nan | nan | nan | R | nan | Reserved. | nan |
| `0067` | KA/M | nan | nan | ENUM | R/W | nan | Auto/Manual shortcut. | nan |
| `0068` | KR/S | nan | nan | ENUM | R/W | nan | Run/Stop shortcut. | nan |
| `0069` | KATU | nan | nan | ENUM | R/W | nan | F3 triggers Auto-tune (0/1). | nan |
| `006A` | PWON | nan | nan | ENUM | R/W | nan | 0=PID,1=Manual,2=Stop,3=Restore previous | Select startup behavior after power-on: 0=PID,1=Manual,2=Stop,3=Restore previous. Select startup behavior after power-on: 0=PID,1=Manual,2=Stop,3=Restore previous. |
| `006B` | SFST | nan | nan | ENUM | R/W | nan | Soft-start enable (0/1). | nan |
| `006C` | tRS | nan | nan | ENUM | R/W | nan | 0=PV retrans.,1=SV retrans. | nan |
| `006D` | PFbK | nan | nan | ENUM | R/W | nan | 0=No position fb,1=Enabled | nan |
| `006E` | RESV (RemoteSV) | nan | nan | ENUM | R/W | nan | 0=Panel,1=Remote,2=Remote+panel | nan |
| `006F` | MONI | nan | nan | ENUM | R/W | nan | SV window content selector | nan |
| `0070` | bEAM | nan | nan | ENUM | R/W | nan | Bar graph content selector | nan |
| `0071` | T1UN | nan | nan | ENUM | R/W | nan | 0=Seconds,1=Minutes | nan |
| `0072` | REMS | nan | nan | ENUM | R/W | nan | Manual output authority: 0=panel,1=remote | Manual output authority: 0=panel sets value, 1=remote (Modbus) sets value. Manual output authority: 0=panel sets value, 1=remote (Modbus) sets value. |

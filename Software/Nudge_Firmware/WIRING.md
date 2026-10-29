# Wiring: main board + 16-pad EMG strip

The design files are in `Hardware/KiCad/NudgePCB` (copied from `Nudge Fall Internship.zip`):
the main board (`Nudge/Nudge.kicad_sch`) and the EMG strip (`NudgeEMGStrip/NudgeFlexStrip.kicad_sch`).
Checked against the ADS1298 datasheet (TI SBAS459K). All pins live in `BoardConfig.h`.

## Main board (XIAO ESP32-S3)

| XIAO pin | Net | Use |
|---|---|---|
| D0 | /STRIP_DRDY | EMG strip data-ready (was the MyoWare input) |
| D1 | /FSR_THUMB | thumb force sensor (J3), 10k pull-down |
| D2 | /FSR_INDEX | index force sensor (J4), 10k pull-down |
| D3 | /VBAT_SENSE | battery through 100k/100k divider |
| D4, D5 | /M1_IN1, /M1_IN2 | motor 1 (DRV8833 AIN1/AIN2), output on J5 |
| D6 | /MOTOR_EN | DRV8833 SLP; R7 pulls it low (asleep) |
| D7 | /M1_ENC_A | motor 1 encoder channel A (J5 pin 5), 10k pull-up |
| D8, D9 | /M2_IN1, /M2_IN2 | motor 2 (DRV8833 BIN1/BIN2), output on J6 |
| D10 | /M2_ENC_A | motor 2 encoder channel A (J6 pin 5), 10k pull-up |
| bottom pads MTCK, MTDO, MTDI, MTMS (GPIO39–42) | /STRIP_SCLK, /STRIP_DIN, /STRIP_DOUT, /STRIP_CS | EMG strip SPI |

Only encoder channel A is wired (J5/J6 pin 6 is unconnected), so the firmware counts pulses
and takes direction from what it's driving. Motors run from the 9 V boost (U3).

## Connecting the strip

Every edge pin of the XIAO was already in use, so the main board's MyoWare connector J2 has
been replaced (schematic and PCB) by a 12-pin header, **J2 "EMG_Strip"**, wired 1:1 to the
strip's header J3. Its SPI lines go to the XIAO's four bottom pads, which were in the footprint
but unconnected; on a soldered module these are solid board pads. Nothing else is lost.
This is the firmware's default pin setup.

The main board PCB is still an unrouted placement draft (no outline or tracks yet), so J2
needs placing and routing along with everything else.

| Strip J3 pin | Signal | Main board |
|---|---|---|
| 1 | 3V3 | 3V3 |
| 2 | SCLK | MTCK pad (GPIO39) |
| 3 | DIN | MTDO pad (GPIO40) |
| 4 | CS | MTMS pad (GPIO42) |
| 5 | DOUT | MTDI pad (GPIO41) |
| 6 | CLK | not connected |
| 7 | CLKSEL | 3V3 (internal oscillator) |
| 8 | START | GND (firmware starts conversions by command) |
| 9 | DRDY | D0 |
| 10 | PWDN | 3V3 |
| 11 | RESET | 3V3 (firmware resets by command) |
| 12 | GND | GND |

### Bench option: current board, no rework

Uncomment `#define STRIP_ON_BENCH_PINS` in `BoardConfig.h`. This borrows the force-sensor and
encoder pins, so **unplug both force sensors (J3, J4) and both encoders**. Grip limiting is
then off and the motors run on timers instead of encoder feedback. The resistors already on
those pins are harmless: the pull-downs on SCLK and DIN, and the pull-ups on CS and DRDY.

| Strip J3 pin | Signal | Main board |
|---|---|---|
| 2 | SCLK | D1 (thumb force-sensor pin) |
| 3 | DIN | D2 (index force-sensor pin) |
| 4 | CS | D10 (motor 2 encoder pin) |
| 5 | DOUT | D0 (MyoWare pin) |
| 9 | DRDY | D7 (motor 1 encoder pin) |
| others | | as in the table above |

## Strip pads to channels

Pads 1–16 sit in a line at 10 mm pitch; each channel is a neighbouring pair. Pad 17
(30 mm from pad 16) is the reference, driven by RLDOUT through R1 = 1 MΩ ∥ C8 = 1.5 nF to
RLDINV, which matches the datasheet example.

| Channel | + pad | − pad |
|---|---|---|
| 1 | 16 | 15 |
| 2 | 14 | 13 |
| 3 | 12 | 11 |
| 4 | 10 | 9 |
| 5 | 8 | 7 |
| 6 | 6 | 5 |
| 7 | 4 | 3 |
| 8 | 2 | 1 |

The app and Mac controller label channels this way, e.g. "Ch 1 (16/15)".

## ADS1298 settings (constants in `ADS1298.h`)

| Setting | Default | Register |
|---|---|---|
| Sample rate | 250 SPS (low-power mode) | CONFIG1 = 0x06; 500 SPS sets 0x86 |
| Gain | 6 (chip default, ±400 mV range) | CHnSET = 0x00 |
| Reference | internal 2.4 V | CONFIG3 = 0xCC (also RLD on, internal RLD reference) |
| RLD inputs | all 8 channels, both sides | RLD_SENSP = RLD_SENSN = 0xFF |
| Mains notch | 60 Hz (+ harmonics below Nyquist) | in software; 50 for Europe |
| SPI | 4 MHz, mode 1 | 2 µs gaps between command bytes |

Processing on the ESP32: 20 Hz high-pass (4th order), 100 Hz low-pass, mains notch, rectify,
average over 20 ms. The result (microvolts per channel, 50 times a second) goes to the gesture
model and to the app over BLE, along with battery % and the two force readings.

## Bring-up, step by step

Flash for board **XIAO_ESP32S3**, open the Serial Monitor at 115200 baud, and type commands.
**The hand opens by itself at power-up** (homing): keep fingers clear.

1. **Motors:** `status` shows both motors, force sensors and battery. If a motor runs the
   wrong way (closes on `retract`), flip its sign in `CLOSE_DIRECTION` in `BoardConfig.h`.
   Then run `calibrate` once to learn each motor's travel; it's saved.
2. **Strip found:** at boot it should print `ID register 0x92` (or `0xD2` for an ADS1298R).
   Anything else means SPI isn't working: check 3V3, GND, CS, SCLK, DIN and DOUT.
3. `ads diag on`: prints, every second, the sample rate (should be ~250), bad frames (should
   stay 0), and per-channel raw swing and filtered EMG level.
4. `ads mode shorted`: inputs shorted inside the chip. EMG level should be close to 0 µV on
   every channel. This is the noise floor.
5. `ads mode supply` or `ads mode temp`: large, steady readings. Proves real data is coming through.
6. `ads mode test`: internal 1 Hz square wave of about 1 mV (see the caveat below).
7. `ads mode electrodes`: back to normal. Put the strip on, relax and then squeeze: the EMG
   level should rise clearly on several channels.

Other commands: `grasp`, `retract`, `motor <1|2> <percent closed>`, `stop`, `ai_start`, `ai_stop`.

## Things in the schematics worth checking

- **Strip VCAP3 capacitor (fixed in the design files).** The datasheet says to connect 1 µF
  from VCAP3 to AVSS. The strip had C7 (0.1 µF) and C3 (1 µF) in series, only about 0.09 µF.
  In the repo's design C7 is now **R2, a 0 Ω jumper** in the same 0603 spot, so C3 sits
  directly on VCAP3. Strips already made from the Oct 27 Gerbers need the same rework:
  bridge C7's pads or swap it for a 0 Ω part.
- **Strip test signal may not work.** TESTP_PACE_OUT1/2 are tied to 3V3, which is what the
  datasheet says to do with unused pins. But it also says the internal test signal is driven
  out on those pins, so `ads mode test` may give a distorted or flat result. Use steps 4–5
  to verify SPI instead.
- **Analog and digital power share the XIAO's 3V3,** which also has the radio and the motor
  driver logic on it. If the shorted-input noise in step 4 is high, a ferrite bead or small
  LDO for the strip's AVDD is the usual fix.
- **Motor stall current.** The Adafruit DRV8833 breakout has no current limit set, and homing
  and `calibrate` find the end stops by stalling the motors briefly (at 40% power, for at
  most 0.3 s after movement stops). Check the motors and gearboxes tolerate this.
- **DRV8833 footprint mismatch (main board, pre-existing).** The schematic symbol has pins
  15 (VMOTOR+) and 16 (GND) that the `NudgeParts:Adafruit_DRV8833` footprint lacks, so the
  PCB's parity check flags them. Make sure the breakout still gets ground through another pin.
- **D6 (motor enable) is also the S3's boot-log serial pin,** so it may toggle during boot.
  The motor inputs are pulled low (R8–R11), so the motors only coast, but it's worth knowing.

# Wiring: Nudge v2 rev2 board + MyoWare 2.0

Design files: `Hardware/KiCad/NudgeV2Rev2` (schematic, netlist, PDF and the board team's
`nudge_firmware_notes.txt`). All pins live in `BoardConfig.h`.

The firmware for the 16-pad ADS1298 EMG strip is on the `16-pad-emg-strip` branch. Rev2 has no
connector for the strip.

## Pins (XIAO ESP32-S3)

| XIAO pin | GPIO | Net | Use |
|---|---|---|---|
| D0 | 1 | EMG | MyoWare ENV output (J2 pin 3), analog |
| D1 | 2 | FSR_THUMB | J3, not used on rev2 |
| D2 | 3 | FSR_INDEX | J4, not used on rev2 |
| D3 | 4 | VBAT_SENSE | battery through a 100k/100k divider |
| D4, D5 | 5, 6 | M1_IN1, M1_IN2 | DRV8833 AIN1/AIN2, motor on J5 |
| D6 | 43 | MOTOR_EN | DRV8833 SLEEP; R7 pulls it low (asleep) |
| D7 | 44 | M1_ENC_A | encoder channel A (J5 pin 5), 10k pull-up |
| D8, D9, D10 | 7, 8, 9 | M2_IN1, M2_IN2, M2_ENC_A | second motor (J6), not fitted |
| LED_BUILTIN | 21 | | status LED, on when LOW |

Connectors:

| Connector | Pin 1 | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|---|
| J2 MyoWare | 3V3 | GND | ENV | | | |
| J5 motor | motor + (AOUT1) | motor − (AOUT2) | **+9V** (encoder power) | GND | encoder A | not connected |

## What the firmware does

- **Power-up:** motor driver asleep first, LED blinks once. The hand is taken as open where it
  is: the motor winds a line on a spool and elastic cords open the hand, so there's no end
  stop to find when opening. Opening means winding back to encoder count 0.
- **EMG:** ENV is sampled at 1 kHz with `analogReadMilliVolts` and averaged into 50 Hz frames
  (millivolts). The app's Train AI records squeeze and relax with the sensor on, trains a
  model on the phone and sends it to the board. With `ai_start` from the app, squeezing
  closes the hand and relaxing opens it. A detected gesture must stay above 80% confidence
  for 3 frames in a row before the hand moves.
- **Closing:** stops at the target, or when the encoder stops counting for 150 ms (hand closed
  or holding something). The driver then brakes, which holds the line.
- **Safety:** any single move stops after 2 s. The DRV8833 limits current to about 1 A (0.2 Ω
  sense resistors), so stalls are gentle. On app disconnect, gestures stop moving the hand.
- **Battery:** below 3.5 V the LED flashes briefly every second. Below 3.3 V the motor stops,
  the LED blinks slowly, and the hand can open but not close until it's charged (USB-C).

These values are in `BoardConfig.h`: `MOVE_TIMEOUT_MS`, `STALL_MS`, `BATTERY_LOW_V`,
`BATTERY_EMPTY_V`, `CLOSE_DIRECTION`.

## Bring-up, step by step

Flash for board **XIAO_ESP32S3** and open the Serial Monitor at 115200 baud. **Power up with
the hand open.**

1. **Before plugging in the motor, check the encoder's output voltage** (see the first issue
   below).
2. `status`: shows the motor position, battery voltage and driver state.
3. `emg diag on`: prints the MyoWare level every second. Relaxed should be low (tens of mV),
   and a firm squeeze should rise clearly (hundreds of mV or more). If it barely moves, turn the
   MyoWare's gain trimmer up or move the sensor along the forearm. If a squeeze hits about
   3300 mV, turn the gain down. `emg diag off` stops it.
4. **Motor direction:** `grasp 80` should wind the line in (close a little). If it opens
   instead, flip `CLOSE_DIRECTION` in `BoardConfig.h`. `retract` opens again.
5. **Learn the travel, once:** with the hand open and empty, `calibrate`. It closes until the
   motor stalls, records the pulse count, then opens. The count is saved, and `status` shows it.
6. In the app: connect, run Train AI (squeeze / relax), then turn on AI control.

Other commands: `grasp [0-255] [speed]`, `retract`, `motor 1 <percent closed>`, `stop`,
`ai_start`, `ai_stop`.

## Things in the schematic worth checking

- **Encoder power is +9V (J5 pin 3).** Many N20 hall encoders output a signal that swings to
  their supply voltage. If yours does, D7 would see 9 V, and that can destroy the XIAO
  (GPIOs are 3.3 V max). Measure encoder A with the encoder powered and the XIAO
  unplugged. If it goes above 3.3 V, power the encoder from 3V3 instead, or add a divider or
  clamp on the signal. Only an open-collector output, pulled up by R5 to 3V3, is safe as wired.
- **The XIAO's own battery pin (U1 pin 23) is on the switched VBAT net.** The XIAO has a
  built-in charger that feeds this pin whenever its USB-C is plugged in. So with the XIAO on
  USB, VBAT (and therefore the 9 V boost and the motor) can be live even with the power switch
  off. Also, its charger and the board's MCP73831 both charge the same cell. Treat the hand as
  powered whenever the XIAO is on USB, and check with the board's designers whether pin 23
  should be left unconnected.
- **Spool drift.** Opening is "back to count 0", and a single-channel encoder can't see
  which way the spool turns. If the hand ends up not fully open after a jam, power-cycle
  with the hand open, or run `calibrate` again. Unwinding past 0 would start winding the line
  the other way, so the firmware never goes below 0.
- **D6 (motor enable) is also the S3's boot-log serial pin,** so it may toggle during boot.
  AIN1/AIN2 are pulled low (R8/R9), so the motor only coasts. D7 is the matching RX pin.
- **DRV8833 nFAULT is not connected,** so the firmware can't see over-current or
  over-temperature shutdowns. They show up as a stall.
- **MyoWare levels vary per person and placement,** so the gesture model needs training, and a
  quick check after re-fitting the sensor.

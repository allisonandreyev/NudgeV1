/*
  =============================================================================
  NUDGE — EMG-Controlled Hand Exoskeleton Firmware
  =============================================================================
  Board:      Seeed XIAO ESP32-C6
  IDE setup:  Tools > Board > esp32 > "XIAO_ESP32C6"
              (install "esp32" board package by Espressif Systems first,
              via Boards Manager, if not already installed)
  Library:    "ESP32Servo" by Kevin Harrington / madhephaestus
              (Sketch > Include Library > Manage Libraries > search ESP32Servo)
  Serial:     115200 baud, Serial Monitor / Plotter for live tuning

  WHAT THIS VERSION DOES
  -----------------------
  - Reads 3 MyoWare EMG channels + 2 FSR channels + 1 shared servo position
    feedback channel, all smoothed with a simple exponential moving average.
  - Each EMG channel is a simple THRESHOLD (on/off) trigger, not proportional:
    crossing EMG_THRESHOLD_ON commands its paired servos CLOSED, dropping
    below EMG_THRESHOLD_OFF commands them back OPEN. The gap between the two
    thresholds is deliberate hysteresis so a noisy signal near the threshold
    doesn't chatter open/close.
  - Each EMG channel drives a PAIR of servos (2 servos per channel, 3
    channels x 2 = 6 servos). The pairing is just an array below — remap it
    freely once you know which servo is on which finger.
  - The 2 FSR sensors do CLOSED-LOOP GRIP-FORCE LIMITING: once a protected
    servo pair is closing and its FSR crosses FSR_MAX_FORCE, that pair stops
    closing further (holds position) regardless of the EMG state, until the
    force drops back below FSR_RELEASE_FORCE. Only 2 of the 3 servo pairs
    have an FSR (only 2 FSRs exist physically) — see FSR_PROTECTS_PAIR.
  - The shared servo feedback signal (GPIO6) is a single system-wide analog
    reading, NOT per-servo (confirmed hardware limitation — see project
    handoff doc). It's read, smoothed, and reported over Serial as general
    telemetry / a coarse "is anything moving" signal. Don't try to derive
    individual servo angles from it.
  - No BLE in this version — Serial only. Add BLE later once the control
    loop above is validated on the bench.

  WHAT YOU'LL PROBABLY WANT TO TWEAK FIRST
  ------------------------------------------
  - All the values in the "TUNABLE CALIBRATION VALUES" section below.
  - EMG_SERVO_PAIRS / FSR_PROTECTS_PAIR if your finger wiring differs.
  - SERVO_MIN_PULSE_US / SERVO_MAX_PULSE_US once you know your exact servo's
    datasheet pulse range (these are generic 270 deg digital servo defaults).
  =============================================================================
*/

#include <ESP32Servo.h>

// =============================================================================
// PIN DEFINITIONS  (see project pinout table for the full derivation)
// =============================================================================

// ---- Analog inputs (ADC1 — only these 6 pins on this chip are ADC-capable) ----
#define PIN_EMG1            0   // XIAO D0  / A0  - GPIO0 - ADC1_CH0 - MyoWare EMG sensor 1
#define PIN_EMG2            1   // XIAO D1  / A1  - GPIO1 - ADC1_CH1 - MyoWare EMG sensor 2
#define PIN_EMG3            2   // XIAO D2  / A2  - GPIO2 - ADC1_CH2 - MyoWare EMG sensor 3
#define PIN_FSR_THUMB       4   // back pad (MTMS) - GPIO4 - ADC1_CH4 - FSR under thumb
                                // NOTE: GPIO4 is a boot-strapping pin. Plain analog
                                // input is fine; do NOT add pull resistors here or
                                // drive this line externally during power-up/reset.
#define PIN_FSR_MIDRING     5   // back pad (MTDI) - GPIO5 - ADC1_CH5 - FSR under middle/ring pad
                                // NOTE: same boot-strapping caution as GPIO4 above.
#define PIN_SERVO_FEEDBACK  6   // back pad (MTCK) - GPIO6 - ADC1_CH6 - SHARED servo position
                                // feedback for all 6 servos combined (system-level only,
                                // not resolvable to an individual servo).

// ---- Servo PWM outputs ----
#define PIN_SERVO1          21  // XIAO D3  / SS  (SPI)  - GPIO21
#define PIN_SERVO2          22  // XIAO D4  / SDA (I2C)  - GPIO22
#define PIN_SERVO3          23  // XIAO D5  / SCL (I2C)  - GPIO23
#define PIN_SERVO4          16  // XIAO D6  / TX  (UART) - GPIO16
#define PIN_SERVO5          17  // XIAO D7  / RX  (UART) - GPIO17
#define PIN_SERVO6          19  // XIAO D8  / SCK (SPI)  - GPIO19

// ---- Spare / reserved, not wired to anything yet ----
// XIAO D9          - GPIO20 (MISO) - spare, unassigned
// XIAO D10         - GPIO18 (MOSI) - spare, unassigned
// back pad (MTDO)  - GPIO7  - spare, digital-only (not ADC-capable), unassigned


// =============================================================================
// TUNABLE CALIBRATION VALUES — EDIT THESE AS YOU BENCH-TEST
// =============================================================================

// ---- EMG thresholds (raw 12-bit ADC counts, 0-4095) ----
// ON  = level that must be crossed (rising) to command that channel's servo
//       pair CLOSED.
// OFF = level that must be dropped below (falling) to command it back OPEN.
// OFF should always be noticeably lower than ON — that gap is the hysteresis
// band that keeps a noisy EMG signal from chattering the servos.
int EMG_THRESHOLD_ON[3]  = { 2000, 2000, 2000 };  // [EMG1, EMG2, EMG3]
int EMG_THRESHOLD_OFF[3] = { 1500, 1500, 1500 };  // [EMG1, EMG2, EMG3]

// ---- FSR grip-force limiting (raw 12-bit ADC counts, 0-4095) ----
// MAX     = force level at which a closing servo pair stops advancing further.
// RELEASE = force must drop below this before that pair is allowed to resume
//           closing (hysteresis, same idea as the EMG thresholds above).
int FSR_MAX_FORCE[2]     = { 3000, 3000 };  // [FSR_THUMB, FSR_MIDRING]
int FSR_RELEASE_FORCE[2] = { 2500, 2500 };  // [FSR_THUMB, FSR_MIDRING]

// ---- Servo angle endpoints, per servo (logical 0-180 scale, see note below) ----
// NOTE ON RANGE: these are 270 degree servos. The Servo library's write(angle)
// takes a LOGICAL 0-180 value and maps it linearly across whatever physical
// pulse-width range you attach() with (see SERVO_MIN_PULSE_US / MAX below).
// If MIN/MAX pulse correspond to the servo's true 0 deg / 270 deg endpoints,
// then write(180) drives it to the physical 270 deg end and write(90) drives
// it to the physical 135 deg midpoint — write() angle is NOT physical degrees.
// Tune the two arrays below empirically by watching the actual hand.
int SERVO_OPEN_ANGLE[6]   = {   0,   0,   0,   0,   0,   0 };  // relaxed/open position
int SERVO_CLOSED_ANGLE[6] = { 180, 180, 180, 180, 180, 180 };  // fully gripped position

// Pulse width range for the physical servos (microseconds). Generic default
// for 270 deg digital servos — confirm against your servo's actual datasheet
// and adjust if the endpoints feel clipped or don't reach full travel.
const int SERVO_MIN_PULSE_US = 500;
const int SERVO_MAX_PULSE_US = 2500;

// How fast servos sweep from current position to target (bigger step / shorter
// interval = faster & snappier; smaller step / longer interval = smoother &
// gentler on the Bowden cables).
const int SERVO_STEP_DEG              = 2;   // degrees per step (logical scale)
const unsigned long SERVO_STEP_INTERVAL_MS = 15;  // ms between steps

// Exponential moving average smoothing factor for each signal type, 0.0-1.0.
// Smaller = smoother / slower to respond. Larger = snappier / noisier.
const float EMG_SMOOTHING_ALPHA       = 0.25f;
const float FSR_SMOOTHING_ALPHA       = 0.25f;
const float SERVO_FB_SMOOTHING_ALPHA  = 0.20f;

// How often to sample sensors and how often to print debug output.
const unsigned long SENSOR_SAMPLE_INTERVAL_MS = 8;    // ~125 Hz
const unsigned long DEBUG_PRINT_INTERVAL_MS   = 250;  // 4x/sec status print

// Minimum change on the shared servo feedback signal (raw ADC counts) before
// we consider the system to have actually moved, vs. just ADC/EMG noise.
const int SERVO_FB_MOVEMENT_DELTA = 40;


// =============================================================================
// STRUCTURAL CONFIG — which EMG channel drives which pair of servos, and
// which FSR protects which pair. Indices refer to the SERVO_* arrays above
// (0 = Servo1 ... 5 = Servo6) and the EMG_THRESHOLD_* arrays (0 = EMG1, etc).
// REMAP THESE if your actual finger/servo wiring is different.
// =============================================================================

const int NUM_EMG    = 3;
const int NUM_SERVOS = 6;
const int NUM_FSR    = 2;

// EMG channel i -> { servo index A, servo index B }
int EMG_SERVO_PAIRS[NUM_EMG][2] = {
  { 0, 1 },  // EMG1 -> Servo1 & Servo2
  { 2, 3 },  // EMG2 -> Servo3 & Servo4
  { 4, 5 },  // EMG3 -> Servo5 & Servo6
};

// FSR i -> which EMG/servo pair index (0, 1, or 2) it force-limits.
// Only 2 FSRs exist physically, so only 2 of the 3 pairs get force limiting.
// Pair 2 (Servo5 & Servo6 / EMG3) has none — add a 3rd FSR here if needed.
int FSR_PROTECTS_PAIR[NUM_FSR] = {
  0,  // FSR_THUMB   -> protects pair 0 (Servo1 & Servo2)
  1,  // FSR_MIDRING -> protects pair 1 (Servo3 & Servo4)
};


// =============================================================================
// RUNTIME STATE — you shouldn't need to edit below this line
// =============================================================================

int emgPins[NUM_EMG]  = { PIN_EMG1, PIN_EMG2, PIN_EMG3 };
int fsrPins[NUM_FSR]  = { PIN_FSR_THUMB, PIN_FSR_MIDRING };
int servoPins[NUM_SERVOS] = { PIN_SERVO1, PIN_SERVO2, PIN_SERVO3,
                               PIN_SERVO4, PIN_SERVO5, PIN_SERVO6 };

Servo servos[NUM_SERVOS];

float emgSmooth[NUM_EMG];
float fsrSmooth[NUM_FSR];
float servoFbSmooth;
float servoFbSmoothPrev;

enum GripState { GRIP_OPEN, GRIP_CLOSED };
GripState pairState[NUM_EMG];       // current commanded state per EMG/servo pair
bool pairForceHeld[NUM_EMG];        // true while that pair is force-limited (holding)

int currentAngle[NUM_SERVOS];       // angle we've actually commanded so far (for stepping)
int targetAngle[NUM_SERVOS];        // angle we're stepping toward

unsigned long lastSampleTime     = 0;
unsigned long lastServoStepTime  = 0;
unsigned long lastDebugPrintTime = 0;


// =============================================================================
// SETUP
// =============================================================================

void setup() {
  Serial.begin(115200);
  delay(300); // let USB CDC settle on the C6 before printing

  analogReadResolution(12); // 0-4095, matches the thresholds tuned above

  // Seed the EMA filters with a real first reading instead of 0, so we don't
  // get a slow ramp-up bias when the sketch first starts.
  for (int i = 0; i < NUM_EMG; i++) {
    emgSmooth[i] = analogRead(emgPins[i]);
    pairState[i] = GRIP_OPEN;
    pairForceHeld[i] = false;
  }
  for (int i = 0; i < NUM_FSR; i++) {
    fsrSmooth[i] = analogRead(fsrPins[i]);
  }
  servoFbSmooth = analogRead(PIN_SERVO_FEEDBACK);
  servoFbSmoothPrev = servoFbSmooth;

  // Attach servos and drive them to the OPEN position immediately so the hand
  // doesn't power up mid-grip.
  for (int i = 0; i < NUM_SERVOS; i++) {
    servos[i].attach(servoPins[i], SERVO_MIN_PULSE_US, SERVO_MAX_PULSE_US);
    currentAngle[i] = SERVO_OPEN_ANGLE[i];
    targetAngle[i]  = SERVO_OPEN_ANGLE[i];
    servos[i].write(currentAngle[i]);
  }

  Serial.println(F("NUDGE firmware up. EMG threshold mode, FSR grip-limiting active, BLE disabled."));
}


// =============================================================================
// MAIN LOOP
// =============================================================================

void loop() {
  unsigned long now = millis();

  if (now - lastSampleTime >= SENSOR_SAMPLE_INTERVAL_MS) {
    lastSampleTime = now;
    sampleSensors();
    updateGripStates();
  }

  if (now - lastServoStepTime >= SERVO_STEP_INTERVAL_MS) {
    lastServoStepTime = now;
    stepServosTowardTargets();
  }

  if (now - lastDebugPrintTime >= DEBUG_PRINT_INTERVAL_MS) {
    lastDebugPrintTime = now;
    printDebugStatus();
  }
}


// =============================================================================
// SENSOR SAMPLING
// =============================================================================

// Simple exponential moving average: smooth += alpha * (raw - smooth)
float emaUpdate(float smooth, int raw, float alpha) {
  return smooth + alpha * ((float)raw - smooth);
}

void sampleSensors() {
  for (int i = 0; i < NUM_EMG; i++) {
    int raw = analogRead(emgPins[i]);
    emgSmooth[i] = emaUpdate(emgSmooth[i], raw, EMG_SMOOTHING_ALPHA);
  }
  for (int i = 0; i < NUM_FSR; i++) {
    int raw = analogRead(fsrPins[i]);
    fsrSmooth[i] = emaUpdate(fsrSmooth[i], raw, FSR_SMOOTHING_ALPHA);
  }
  servoFbSmoothPrev = servoFbSmooth;
  int fbRaw = analogRead(PIN_SERVO_FEEDBACK);
  servoFbSmooth = emaUpdate(servoFbSmooth, fbRaw, SERVO_FB_SMOOTHING_ALPHA);
}


// =============================================================================
// GRIP CONTROL LOGIC (EMG threshold + FSR force limiting)
// =============================================================================

void updateGripStates() {
  for (int pair = 0; pair < NUM_EMG; pair++) {

    // ---- 1. EMG threshold state machine (with hysteresis) ----
    if (pairState[pair] == GRIP_OPEN && emgSmooth[pair] > EMG_THRESHOLD_ON[pair]) {
      pairState[pair] = GRIP_CLOSED;
    } else if (pairState[pair] == GRIP_CLOSED && emgSmooth[pair] < EMG_THRESHOLD_OFF[pair]) {
      pairState[pair] = GRIP_OPEN;
      pairForceHeld[pair] = false; // releasing the grip always clears any force-hold
    }

    // ---- 2. FSR grip-force limiting (only applies to pairs with an FSR) ----
    int fsrIndex = -1;
    for (int f = 0; f < NUM_FSR; f++) {
      if (FSR_PROTECTS_PAIR[f] == pair) { fsrIndex = f; break; }
    }

    if (fsrIndex >= 0 && pairState[pair] == GRIP_CLOSED) {
      if (!pairForceHeld[pair] && fsrSmooth[fsrIndex] > FSR_MAX_FORCE[fsrIndex]) {
        pairForceHeld[pair] = true;   // force limit hit: stop closing further
      } else if (pairForceHeld[pair] && fsrSmooth[fsrIndex] < FSR_RELEASE_FORCE[fsrIndex]) {
        pairForceHeld[pair] = false;  // force eased off: allow closing to resume
      }
    }

    // ---- 3. Translate state into target angles for this pair's 2 servos ----
    int servoA = EMG_SERVO_PAIRS[pair][0];
    int servoB = EMG_SERVO_PAIRS[pair][1];

    if (pairState[pair] == GRIP_CLOSED) {
      if (pairForceHeld[pair]) {
        // Hold exactly where we are — don't advance toward closed any further.
        targetAngle[servoA] = currentAngle[servoA];
        targetAngle[servoB] = currentAngle[servoB];
      } else {
        targetAngle[servoA] = SERVO_CLOSED_ANGLE[servoA];
        targetAngle[servoB] = SERVO_CLOSED_ANGLE[servoB];
      }
    } else {
      // Always allow opening/releasing, force-limiting never blocks this.
      targetAngle[servoA] = SERVO_OPEN_ANGLE[servoA];
      targetAngle[servoB] = SERVO_OPEN_ANGLE[servoB];
    }
  }
}


// =============================================================================
// SERVO MOTION (gradual step toward target, gentler on the Bowden cables
// than snapping straight to the target angle)
// =============================================================================

void stepServosTowardTargets() {
  for (int i = 0; i < NUM_SERVOS; i++) {
    if (currentAngle[i] == targetAngle[i]) continue;

    if (currentAngle[i] < targetAngle[i]) {
      currentAngle[i] = min(currentAngle[i] + SERVO_STEP_DEG, targetAngle[i]);
    } else {
      currentAngle[i] = max(currentAngle[i] - SERVO_STEP_DEG, targetAngle[i]);
    }
    servos[i].write(currentAngle[i]);
  }
}


// =============================================================================
// DEBUG / TELEMETRY (Serial only in this version — see header note re: BLE)
// =============================================================================

void printDebugStatus() {
  Serial.print(F("EMG["));
  for (int i = 0; i < NUM_EMG; i++) {
    Serial.print(emgSmooth[i], 0);
    Serial.print(pairState[i] == GRIP_CLOSED ? F("C") : F("O"));
    if (pairForceHeld[i]) Serial.print(F("*")); // * = force-held, not fully closed
    if (i < NUM_EMG - 1) Serial.print(F(", "));
  }
  Serial.print(F("]  FSR["));
  for (int i = 0; i < NUM_FSR; i++) {
    Serial.print(fsrSmooth[i], 0);
    if (i < NUM_FSR - 1) Serial.print(F(", "));
  }
  Serial.print(F("]  ServoFB="));
  Serial.print(servoFbSmooth, 0);
  Serial.print(abs(servoFbSmooth - servoFbSmoothPrev) > SERVO_FB_MOVEMENT_DELTA
                ? F(" (moving)") : F(" (idle)"));

  Serial.print(F("  Angles["));
  for (int i = 0; i < NUM_SERVOS; i++) {
    Serial.print(currentAngle[i]);
    if (i < NUM_SERVOS - 1) Serial.print(F(","));
  }
  Serial.println(F("]"));
}


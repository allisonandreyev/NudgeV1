#include "MotorController.h"

// ======================= Motor =======================

static void IRAM_ATTR encoderISR(void* arg)
{
    Motor* m = static_cast<Motor*>(arg);
    m->counts += m->dir;   // single-channel encoder: direction comes from the command
}

void Motor::begin(int pin1, int pin2, int encA, bool reversed)
{
    in1 = pin1; in2 = pin2; enc = encA; rev = reversed;
    ledcAttach(in1, PWM_FREQ_HZ, PWM_RES_BITS);
    ledcAttach(in2, PWM_FREQ_HZ, PWM_RES_BITS);
    ledcWrite(in1, 0);
    ledcWrite(in2, 0);
    if (enc >= 0) {
        pinMode(enc, INPUT_PULLUP);
        attachInterruptArg(digitalPinToInterrupt(enc), encoderISR, this, RISING);
    }
}

// signedPct > 0 closes the hand (counts up), < 0 opens, 0 coasts
void Motor::drive(int signedPct)
{
    signedPct = constrain(signedPct, -100, 100);
    dir = (signedPct > 0) ? 1 : (signedPct < 0 ? -1 : 0);

    const uint32_t maxDuty = (1UL << PWM_RES_BITS) - 1;
    uint32_t duty = (uint32_t)abs(signedPct) * maxDuty / 100;
    bool forward = (signedPct > 0) != rev;

    if (signedPct == 0) {            // both low = coast
        ledcWrite(in1, 0);
        ledcWrite(in2, 0);
    } else if (forward) {
        ledcWrite(in2, 0);
        ledcWrite(in1, duty);
    } else {
        ledcWrite(in1, 0);
        ledcWrite(in2, duty);
    }
}

void Motor::moveTo(long tgt, uint8_t speedPct)
{
    target = tgt;
    speed = constrain(speedPct, (uint8_t)MIN_SPEED_PCT, (uint8_t)100);
    moveStart = lastProgressMs = millis();
    lastCounts = counts;
    st = MotorState::MOVING;
}

void Motor::jog(int signedSpeedPct, uint32_t ms)
{
    jogEnd = millis() + ms;
    st = MotorState::JOGGING;
    drive(signedSpeedPct);
}

void Motor::coast()
{
    drive(0);
    if (active()) st = MotorState::IDLE;
}

void Motor::update()
{
    uint32_t now = millis();

    if (st == MotorState::JOGGING) {
        if ((int32_t)(now - jogEnd) >= 0) { drive(0); st = MotorState::IDLE; }
        return;
    }
    if (st != MotorState::MOVING) return;

    long pos = counts;
    long err = target - pos;

    if (labs(err) <= POSITION_TOLERANCE) { drive(0); st = MotorState::ARRIVED; return; }
    if (now - moveStart > MOVE_TIMEOUT_MS) { drive(0); st = MotorState::TIMEOUT; return; }

    if (pos != lastCounts) {
        lastCounts = pos;
        lastProgressMs = now;
    } else if (now - moveStart > STALL_GRACE_MS && now - lastProgressMs > STALL_TIME_MS) {
        drive(0);                        // stalled = gripping an object or jammed
        st = MotorState::STALLED;
        return;
    }

    long pct = speed;
    if (labs(err) < SLOWDOWN_COUNTS) {
        pct = (long)speed * labs(err) / SLOWDOWN_COUNTS;
        if (pct < MIN_SPEED_PCT) pct = MIN_SPEED_PCT;
    }
    drive(err > 0 ? pct : -pct);
}

// =================== MotorController ===================

Motor         MotorController::fingers;
Motor         MotorController::thumb;
QueueHandle_t MotorController::cmdQueue = nullptr;
bool     MotorController::aiActuationEnabled = false;
bool     MotorController::closing = false;
bool     MotorController::thumbPending = false;
uint32_t MotorController::thumbStartAt = 0;
uint32_t MotorController::lastControlUs = 0;
uint8_t  MotorController::pendingThumbSpeed = DEFAULT_SPEED_PCT;
int      MotorController::forceMv = 0;
int      MotorController::batteryMv = 0;

struct QueuedCmd { char text[64]; };

void MotorController::Init()
{
    // Driver asleep before anything else so the motors can't twitch at boot
    if (PIN_MOTOR_EN >= 0) {
        pinMode(PIN_MOTOR_EN, OUTPUT);
        digitalWrite(PIN_MOTOR_EN, LOW);
    }
    fingers.begin(PIN_M1_IN1, PIN_M1_IN2, PIN_M1_ENC_A, M1_REVERSED);
    thumb.begin(PIN_M2_IN1, PIN_M2_IN2, PIN_M2_ENC_A, M2_REVERSED);
    if (PIN_ESTOP_BUTTON >= 0) pinMode(PIN_ESTOP_BUTTON, INPUT_PULLUP);

    cmdQueue = xQueueCreate(8, sizeof(QueuedCmd));

    // No homing sensor: the device must be OPEN at power-on. That spot becomes 0.
    fingers.zero();
    thumb.zero();
    Serial.println(">> Motors ready. Position zeroed (hand must start OPEN).");
}

void MotorController::ParseCommand(String cmd)
{
    if (!cmdQueue) return;
    QueuedCmd q{};
    cmd.trim();
    strncpy(q.text, cmd.c_str(), sizeof(q.text) - 1);
    xQueueSend(cmdQueue, &q, 0);
}

void MotorController::Update()
{
    QueuedCmd q;
    while (cmdQueue && xQueueReceive(cmdQueue, &q, 0) == pdTRUE) Execute(q.text);

    if (PIN_ESTOP_BUTTON >= 0 && digitalRead(PIN_ESTOP_BUTTON) == LOW &&
        (fingers.active() || thumb.active())) {
        StopAll("stop button");
    }

    // Grip force limit
    if (closing && forceMv >= FSR_STOP_MV && (fingers.active() || thumb.active() || thumbPending)) {
        StopAll("force limit");
    }

    // Delayed thumb start
    if (thumbPending && (int32_t)(millis() - thumbStartAt) >= 0) {
        thumbPending = false;
        thumb.moveTo(M2_CLOSED_COUNTS, pendingThumbSpeed);
    }

    uint32_t nowUs = micros();
    if (nowUs - lastControlUs >= 1000000UL / CONTROL_RATE_HZ) {
        lastControlUs = nowUs;
        fingers.update();
        thumb.update();
    }
    UpdateEnable();
}

void MotorController::UpdateEnable()
{
    if (PIN_MOTOR_EN < 0) return;
    bool needAwake = fingers.active() || thumb.active();
    digitalWrite(PIN_MOTOR_EN, needAwake ? HIGH : LOW);  // sleep when idle
}

void MotorController::MoveGrasp(uint8_t speedPct)
{
    if (batteryMv > 1000 && batteryMv < VBAT_MIN_MV) {
        Serial.printf(">> Grasp refused: battery low (%d mV)\n", batteryMv);
        return;
    }
    closing = true;
    fingers.moveTo(M1_CLOSED_COUNTS, speedPct);
    pendingThumbSpeed = speedPct;
    thumbPending = true;
    thumbStartAt = millis() + THUMB_DELAY_MS;
    Serial.println(">> Grasp");
}

void MotorController::MoveRelease(uint8_t speedPct)
{
    closing = false;
    thumbPending = false;
    fingers.moveTo(0, speedPct);
    thumb.moveTo(0, speedPct);
    Serial.println(">> Release");
}

void MotorController::StopAll(const char* reason)
{
    thumbPending = false;
    fingers.coast();
    thumb.coast();
    if (PIN_MOTOR_EN >= 0) digitalWrite(PIN_MOTOR_EN, LOW);
    Serial.printf(">> Stop (%s)\n", reason);
}

const char* MotorController::StateName(MotorState s)
{
    switch (s) {
        case MotorState::IDLE:    return "idle";
        case MotorState::MOVING:  return "moving";
        case MotorState::ARRIVED: return "arrived";
        case MotorState::STALLED: return "stalled";
        case MotorState::TIMEOUT: return "timeout";
        case MotorState::JOGGING: return "jogging";
    }
    return "?";
}

void MotorController::Status()
{
    Serial.printf("Fingers: pos %ld target %ld [%s]\n",
                  fingers.position(), fingers.targetPos(), StateName(fingers.state()));
    Serial.printf("Thumb:   pos %ld target %ld [%s]\n",
                  thumb.position(), thumb.targetPos(), StateName(thumb.state()));
    Serial.printf("Force %d mV | Battery %d mV | AI actuation %s\n",
                  forceMv, batteryMv, aiActuationEnabled ? "ON" : "off");
}

void MotorController::Help()
{
    Serial.println("Commands:");
    Serial.println("  grasp [speed%]            close hand (fingers, then thumb)");
    Serial.println("  release [speed%]          open hand (alias: retract)");
    Serial.println("  motor <0|1> <counts> [speed%]   move one motor (0=fingers, 1=thumb)");
    Serial.println("  jog <0|1> <-100..100> <ms>      run one motor for calibration");
    Serial.println("  zero                      set current position as OPEN");
    Serial.println("  stop                      stop everything, disable AI");
    Serial.println("  ai_start / ai_stop        let the AI move the motors");
    Serial.println("  status | help");
}

void MotorController::Execute(const char* cmd)
{
    int a = 0, b = 0, c = 0;
    long target = 0;
    char word[16] = {0};
    int n = sscanf(cmd, "%15s %d %d %d", word, &a, &b, &c);
    if (n < 1) return;
    String w = String(word);
    w.toLowerCase();

    // The old app sends "grasp <angle> <speed>"; with two numbers, use the default speed
    uint8_t spd = (n == 2) ? (uint8_t)a : DEFAULT_SPEED_PCT;

    if (w == "grasp") {
        MoveGrasp(spd);
    } else if (w == "release" || w == "retract") {
        MoveRelease(spd);
    } else if (w == "motor" && sscanf(cmd, "%*s %d %ld %d", &a, &target, &b) >= 2) {
        if (a < 0 || a > 1) { Serial.println(">> motor id must be 0 or 1"); return; }
        Get(a).moveTo(target, (b > 0) ? (uint8_t)b : DEFAULT_SPEED_PCT);
    } else if (w == "jog" && n == 4) {
        if (a < 0 || a > 1) { Serial.println(">> motor id must be 0 or 1"); return; }
        Get(a).jog(b, (uint32_t)constrain(c, 0, 3000));
    } else if (w == "zero") {
        fingers.zero(); thumb.zero();
        Serial.println(">> Zeroed");
    } else if (w == "stop") {
        aiActuationEnabled = false;
        StopAll("command");
    } else if (w == "ai_start") {
        aiActuationEnabled = true;
        Serial.println(">> AI actuation ON");
    } else if (w == "ai_stop") {
        aiActuationEnabled = false;
        Serial.println(">> AI actuation off");
    } else if (w == "status") {
        Status();
    } else if (w == "help") {
        Help();
    } else if (w == "engage" || w == "disengage" || w == "servo") {
        Serial.println(">> Servo/PTO commands don't apply to the N20 build");
    } else {
        Serial.printf(">> Unknown command: %s (type help)\n", cmd);
    }
}
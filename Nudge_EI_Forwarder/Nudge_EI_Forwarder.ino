//simulate 1 simulate, simulate 0 real read
#define SIMULATE 1

const int FLEXOR_PIN   = D0;   // myoware on the forearm flexors (grip)
const int EXTENSOR_PIN = D1;   // myoware on the forearm extensors (open)
const uint32_t RATE_HZ = 200;  // plenty for the myoware ENV output

#if CONFIG_IDF_TARGET_ESP32C6
const int BOOT_BTN = 9;        // BOOT button on XIAO ESP32-C6
#else
const int BOOT_BTN = 0;        // BOOT button on XIAO ESP32-S3
#endif

const uint32_t PERIOD_US = 1000000UL / RATE_HZ;
int simState = 0;              // 0 = REST, 1 = GRIP, 2 = OPEN
bool lastBtn = HIGH;

int simulate(float base, bool active) {
  float noise = random(-40, 41);
  float burst = active ? 900 + 300 * sin(millis() / 90.0) + random(-150, 151) : 0;
  return constrain((int)(base + burst + noise), 0, 3300);
}

void setup() {
  Serial.begin(115200);
  analogReadResolution(12);
  pinMode(BOOT_BTN, INPUT_PULLUP);
}

void loop() {
  static uint32_t next = micros();
  while ((int32_t)(micros() - next) < 0) { }
  next += PERIOD_US;

  int flexor, extensor;
#if SIMULATE
  bool btn = digitalRead(BOOT_BTN);
  if (lastBtn == HIGH && btn == LOW) simState = (simState + 1) % 3;
  lastBtn = btn;
  flexor   = simulate(150, simState == 1);
  extensor = simulate(150, simState == 2);
#else
  flexor   = analogReadMilliVolts(FLEXOR_PIN);
  extensor = analogReadMilliVolts(EXTENSOR_PIN);
#endif

  // Data forwarder format: comma-separated values, one line per sample
  Serial.print(flexor);
  Serial.print(',');
  Serial.println(extensor);
}

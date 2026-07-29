#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

#define PCA9685_ADDR 0x41
#define SERVO_CHANNEL 0

Adafruit_PWMServoDriver pwm = Adafruit_PWMServoDriver(PCA9685_ADDR);

// DSS-M15S range
#define SERVO_MIN 500
#define SERVO_MAX 2500

#define PWM_FREQ 50

uint16_t microsecondsToTicks(uint16_t us) {
  // 50Hz = 20,000us period
  // PCA9685 = 4096 steps
  return (uint16_t)((us * 4096UL) / 20000UL);
}

void setServoAngle(int angle) {
  uint16_t pulse = map(angle, 0, 270, SERVO_MIN, SERVO_MAX);
  uint16_t ticks = microsecondsToTicks(pulse);

  Serial.print("Angle: ");
  Serial.print(angle);
  Serial.print(" deg | Pulse: ");
  Serial.print(pulse);
  Serial.print(" us | PCA Tick: ");
  Serial.println(ticks);

  pwm.setPWM(SERVO_CHANNEL, 0, ticks);
}

void setup() {
  Serial.begin(115200);

  delay(1000);

  Serial.println("\n--- DSS-M15S PCA9685 Test ---");

  Serial.print("Initializing I2C...");
  Wire.begin();
  Serial.println(" OK");

  Serial.print("Connecting to PCA9685 at 0x");
  Serial.println(PCA9685_ADDR, HEX);

  pwm.begin();

  Serial.println("PCA9685 initialized");

  pwm.setOscillatorFrequency(27000000);

  Serial.print("Setting PWM frequency to ");
  Serial.print(PWM_FREQ);
  Serial.println(" Hz");

  pwm.setPWMFreq(PWM_FREQ);

  delay(500);

  Serial.println("Starting servo test");
}

void loop() {

  Serial.println("\nSweeping 0 -> 270");

  for (int angle = 0; angle <= 270; angle++) {
    setServoAngle(angle);
    delay(10);
  }

  Serial.println("Reached 270 degrees");
  delay(1000);


  Serial.println("\nSweeping 270 -> 0");

  for (int angle = 270; angle >= 0; angle--) {
    setServoAngle(angle);
    delay(10);
  }

  Serial.println("Reached 0 degrees");
  delay(1000);
}
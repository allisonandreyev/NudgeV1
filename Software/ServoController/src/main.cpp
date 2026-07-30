#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

// ============================================================
// USER CONFIGURATION
// ============================================================

#define PCA9685_ADDR 0x40

#define I2C_SDA 22
#define I2C_SCL 23

#define PWM_FREQ 50

// Servo calibration
#define SERVO_MIN_US 500
#define SERVO_MAX_US 2500

#define SERVO_MAX_ANGLE 270
#define SERVO_COUNT 16

// Servo update rate
#define SERVO_UPDATE_MS 10

// ============================================================
// Hardware
// ============================================================

Adafruit_PWMServoDriver pwm(PCA9685_ADDR);

// ============================================================
// FreeRTOS
// ============================================================

TaskHandle_t servoTaskHandle;
QueueHandle_t servoQueue;
bool randomMode = false;

// ============================================================
// Data Structures
// ============================================================

struct ServoCommand
{
  uint8_t id;
  float angle;
  float speed;
};

struct ServoState
{
  float current;
  float target;
  float speed;
  bool moving;
};

ServoState servo[SERVO_COUNT];

// ============================================================
// PWM Conversion
// ============================================================

uint16_t microsecondsToTicks(float us) { return (uint32_t)(us * 4096.0 / 20000.0); }

void writeServo(uint8_t id, float angle)
{
  if (id >= SERVO_COUNT) return;

  angle = constrain(angle, 0, SERVO_MAX_ANGLE);
  float pulse = SERVO_MIN_US + ((SERVO_MAX_US - SERVO_MIN_US) * angle / SERVO_MAX_ANGLE);

  pwm.setPWM(id, 0, microsecondsToTicks(pulse));
}

// ============================================================
// Servo Manager Task
// ============================================================

void servoTask(void *parameter)
{
  ServoCommand cmd;

  while (true)
  {
    while (xQueueReceive(servoQueue, &cmd, 0) == pdTRUE)
    {
      if (cmd.id >= SERVO_COUNT) continue;

      servo[cmd.id].target = constrain(
        cmd.angle,
        0,
        SERVO_MAX_ANGLE
      );

      servo[cmd.id].speed = constrain(
        cmd.speed,
        1,
        300
      );

      servo[cmd.id].moving = true;

      Serial.printf(
        "Servo %d -> %.1f degrees @ %.1f deg/sec\n",
        cmd.id,
        servo[cmd.id].target,
        servo[cmd.id].speed
      );
    }

    if (randomMode)
    {
      bool anyMoving = false;

      for (int i = 0; i < SERVO_COUNT; i++)
      {
        if (servo[i].moving)
        {
          anyMoving = true;
          break;
        }
      }

      if (!anyMoving)
      {
        for (int i = 0; i < SERVO_COUNT; i++)
        {
          servo[i].target = random(0, 251);
          servo[i].speed = random(50, 151);
          servo[i].moving = true;
        }
      }
    }

    for (int i = 0; i < SERVO_COUNT; i++)
    {

      if (!servo[i].moving) continue;

      float difference = servo[i].target - servo[i].current;
      float movement = servo[i].speed * (SERVO_UPDATE_MS / 1000.0);

      if (abs(difference) <= movement)
      {
        servo[i].current = servo[i].target;
        servo[i].moving = false;
      }
      else if (difference > 0)
      {
        servo[i].current += movement;
      }
      else
      {
        servo[i].current -= movement;
      }

      writeServo(i, servo[i].current);
    }

        vTaskDelay(
            pdMS_TO_TICKS(SERVO_UPDATE_MS)
        );
    }
}

// ============================================================
// Serial Commands
// ============================================================

String input;

void help()
{
    Serial.println();
    Serial.println("Commands:");
    Serial.println("-----------------------------");
    Serial.println("Servo <id> <angle> <speed>");
    Serial.println("setpulse <id> <microseconds>");
    Serial.println("status");
    Serial.println("random");
    Serial.println("stoprandom");
    Serial.println("help");

    Serial.println();
    Serial.println("Examples:");
    Serial.println("Servo 0 90 30");
    Serial.println("Servo 1 270 20");
    Serial.println("setpulse 0 1500");
    Serial.println();
}

void status()
{
  for (int i = 0; i < SERVO_COUNT; i++)
  {
    Serial.printf(
      "%d: %.1f -> %.1f %s\n",
      i,
      servo[i].current,
      servo[i].target,
      servo[i].moving ? "MOVING" : "IDLE"
    );
  }
}

void parseCommand(String cmd)
{
  cmd.trim();

  if (cmd.equalsIgnoreCase("help"))
  {
    help();
    return;
  }

  if (cmd.equalsIgnoreCase("status"))
  {
    status();
    return;
  }

  if (cmd.equalsIgnoreCase("random")) {
    randomMode = true;
    Serial.println("Random servo mode enabled");
    return;
  }

  if (cmd.equalsIgnoreCase("stoprandom")) {
    randomMode = false;
    Serial.println("Random servo mode disabled");
    return;
  }

  int id;
  float a;
  float speed;

  if (sscanf(cmd.c_str(), "Servo %d %f %f", &id, &a, &speed ) == 3)
  {
    ServoCommand c;

    c.id = id;
    c.angle = a;
    c.speed = speed;

    xQueueSend(servoQueue, &c, portMAX_DELAY);
    return;
  }

  int pulse;

  if (sscanf(cmd.c_str(), "setpulse %d %d", &id, &pulse) == 2) {

    pwm.setPWM(id, 0, microsecondsToTicks(pulse));
    Serial.printf("Servo %d pulse %dus\n", id, pulse);
    return;
  }

  Serial.println("Unknown command");
}

// ============================================================
// Setup
// ============================================================

void setup()
{
    Serial.begin(115200);

    randomSeed(analogRead(0));

    delay(1000);

    Wire.begin(I2C_SDA, I2C_SCL);

    pwm.begin();
    pwm.setOscillatorFrequency(27000000);
    pwm.setPWMFreq(PWM_FREQ);

    delay(20);

    for (int i = 0; i < SERVO_COUNT; i++)
    {
      servo[i].current = 0;
      servo[i].target = 0;
      servo[i].speed = 30;
      servo[i].moving = false;

      writeServo(i, 0);
    }

    servoQueue = xQueueCreate(20, sizeof(ServoCommand));

    xTaskCreate(
      servoTask,
      "ServoTask",
      4096,
      nullptr,
      2,
      &servoTaskHandle
    );

    Serial.println("Servo controller ready");
    help();
}

// ============================================================
// Main Loop
// ============================================================

void loop() 
{
  while (Serial.available()) 
  {
    char c = Serial.read();

    if (c == '\n') 
    {
      parseCommand(input);
      input = "";
    }
    else 
    {
      input += c;
    }
  }

  delay(1);
}
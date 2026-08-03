#include <Arduino.h>
#include <Wire.h>
#include "ServoController.h"
#include <Adafruit_PWMServoDriver.h>

// Static member definitions
ServoState ServoController::servo[SERVO_COUNT] = {};
Adafruit_PWMServoDriver ServoController::pwm(PCA9685_ADDR);
TaskHandle_t ServoController::servoTaskHandle = nullptr;
QueueHandle_t ServoController::servoQueue = nullptr;

bool ServoController::randomMode = false;

void ServoController::WriteServo(uint8_t id, float angle)
{
  if (id >= SERVO_COUNT) return;

  angle = constrain(angle, 0, SERVO_MAX_ANGLE);
  float pulse = SERVO_MIN_US + ((SERVO_MAX_US - SERVO_MIN_US) * angle / SERVO_MAX_ANGLE);

  pwm.setPWM(id, 0, microsecondsToTicks(pulse));
}

void ServoController::ServoTask(void *parameter)
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
        servo[i].current += movement;
      else
        servo[i].current -= movement;
      WriteServo(i, servo[i].current);
    }

    vTaskDelay(pdMS_TO_TICKS(SERVO_UPDATE_MS));
  }
}

void ServoController::Help()
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

void ServoController::Status()
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

void ServoController::ParseCommand(String cmd)
{
  cmd.trim();

  if (cmd.equalsIgnoreCase("help"))
  {
    Help();
    return;
  }

  if (cmd.equalsIgnoreCase("status"))
  {
    Status();
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

void ServoController::Init()
{
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

    WriteServo(i, 0);
  }

  servoQueue = xQueueCreate(20, sizeof(ServoCommand));

  xTaskCreate(
    ServoTask,
    "ServoTask",
    4096,
    nullptr,
    2,
    &servoTaskHandle
  );

  Serial.println("Servo controller ready");
  Help();
}

void ServoController::SetServo(uint8_t id, float angle, float speed)
{
  ServoCommand c;
  c.id = id;
  c.angle = angle;
  c.speed = speed;

  xQueueSend(servoQueue, &c, portMAX_DELAY);
}
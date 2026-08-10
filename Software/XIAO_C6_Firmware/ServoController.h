#pragma once
#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

#define PCA9685_ADDR 0x40

// XIAO ESP32-C6 Default I2C Pins
#define I2C_SDA 4
#define I2C_SCL 5

#define PWM_FREQ 50

// Servo calibration
#define SERVO_MIN_US 500
#define SERVO_MAX_US 2500

#define SERVO_MAX_ANGLE 270
#define SERVO_COUNT 16

// Servo update rate
#define SERVO_UPDATE_MS 10

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

class ServoController
{
  public:
    ServoController();
    ~ServoController();
    static void Init();
    static void ParseCommand(String cmd);
    static void SetServo(uint8_t id, float angle, float speed);

  private:
    static ServoState servo[SERVO_COUNT];
    static void ServoTask(void *parameter);

    // cmdline functions
    static void Status();
    static void Help();
    static void WriteServo(uint8_t id, float angle);

    // helper function
    static uint16_t microsecondsToTicks(float us) { return (uint32_t)(us * 4096.0 / 20000.0); }

    // variables
    static Adafruit_PWMServoDriver pwm;
    static TaskHandle_t servoTaskHandle;
    static QueueHandle_t servoQueue;
    static bool randomMode;
};

#include <Arduino.h>
#include <Wire.h>
#include <Adafruit_PWMServoDriver.h>

// ============================================================
// Configuration
// ============================================================

#define PCA9685_ADDR 0x40

#define I2C_SDA 22
#define I2C_SCL 23

#define PWM_FREQ 50

#define SERVO_MIN_US 500
#define SERVO_MAX_US 2500
#define SERVO_MAX_ANGLE 270

#define SERVO_COUNT 16

#define SERVO_UPDATE_MS 10

// ============================================================
// PCA9685
// ============================================================

Adafruit_PWMServoDriver pwm(PCA9685_ADDR);

// ============================================================
// FreeRTOS
// ============================================================

TaskHandle_t servoTaskHandle = nullptr;
QueueHandle_t servoQueue = nullptr;

// ============================================================
// Servo Data Structures
// ============================================================

struct ServoCommand
{
    uint8_t id;
    float targetAngle;
    float speed; // degrees per update
};

struct ServoState
{
    float currentAngle;
    float targetAngle;
    float speed;
    bool moving;
};

ServoState servos[SERVO_COUNT];

// ============================================================
// Utility Functions
// ============================================================

uint16_t microsecondsToTicks(uint16_t us)
{
    return (uint32_t)us * 4096 / 20000;
}

void writeServo(uint8_t channel, float angle)
{
    angle = constrain(angle, 0.0f, (float)SERVO_MAX_ANGLE);

    uint16_t pulse = map(
        (int)angle,
        0,
        SERVO_MAX_ANGLE,
        SERVO_MIN_US,
        SERVO_MAX_US);

    pwm.setPWM(channel, 0, microsecondsToTicks(pulse));
}

// ============================================================
// Servo Task
// ============================================================

void servoTask(void *pv)
{
    ServoCommand cmd;

    while (true)
    {
        // Process all waiting commands
        while (xQueueReceive(servoQueue, &cmd, 0) == pdTRUE)
        {
            if (cmd.id >= SERVO_COUNT)
                continue;

            servos[cmd.id].targetAngle =
                constrain(cmd.targetAngle, 0.0f, (float)SERVO_MAX_ANGLE);

            servos[cmd.id].speed =
                max(0.1f, cmd.speed);

            servos[cmd.id].moving = true;

            Serial.printf(
                "Servo %d -> %.1f deg @ %.1f deg/update\n",
                cmd.id,
                servos[cmd.id].targetAngle,
                servos[cmd.id].speed);
        }

        // Update every servo
        for (int i = 0; i < SERVO_COUNT; i++)
        {
            ServoState &s = servos[i];

            if (!s.moving)
                continue;

            float error = s.targetAngle - s.currentAngle;

            if (fabs(error) <= s.speed)
            {
                s.currentAngle = s.targetAngle;
                s.moving = false;
            }
            else if (error > 0)
            {
                s.currentAngle += s.speed;
            }
            else
            {
                s.currentAngle -= s.speed;
            }

            writeServo(i, s.currentAngle);
        }

        vTaskDelay(pdMS_TO_TICKS(SERVO_UPDATE_MS));
    }
}

// ============================================================
// Serial Parsing
// ============================================================

String serialBuffer;

void printHelp()
{
    Serial.println();
    Serial.println("Commands:");
    Serial.println("--------------------------------------");
    Serial.println("Servo <id> <angle> <speed>");
    Serial.println("status");
    Serial.println("help");
    Serial.println();
    Serial.println("Examples:");
    Serial.println("Servo 0 180 2");
    Serial.println("Servo 4 90 5");
    Serial.println("Servo 15 270 10");
    Serial.println();
}

void printStatus()
{
    Serial.println();

    for (int i = 0; i < SERVO_COUNT; i++)
    {
        Serial.printf(
            "Servo %2d : Current=%6.1f  Target=%6.1f  %s\n",
            i,
            servos[i].currentAngle,
            servos[i].targetAngle,
            servos[i].moving ? "Moving" : "Idle");
    }

    Serial.println();
}

void parseCommand(String line)
{
    line.trim();

    if (line.length() == 0)
        return;

    if (line.equalsIgnoreCase("help"))
    {
        printHelp();
        return;
    }

    if (line.equalsIgnoreCase("status"))
    {
        printStatus();
        return;
    }

    int id;
    float angle;
    float speed;

    if (sscanf(line.c_str(), "Servo %d %f %f",
               &id,
               &angle,
               &speed) == 3)
    {
        if (id < 0 || id >= SERVO_COUNT)
        {
            Serial.println("Invalid servo ID.");
            return;
        }

        ServoCommand cmd;

        cmd.id = id;
        cmd.targetAngle = angle;
        cmd.speed = speed;

        if (xQueueSend(servoQueue, &cmd, 0) != pdTRUE)
        {
            Serial.println("Servo queue full.");
        }

        return;
    }

    Serial.println("Unknown command. Type 'help'.");
}

// ============================================================
// Setup
// ============================================================

void setup()
{
    Serial.begin(115200);
    delay(1000);

    Wire.begin(I2C_SDA, I2C_SCL);

    pwm.begin();
    pwm.setOscillatorFrequency(27000000);
    pwm.setPWMFreq(PWM_FREQ);

    delay(20);

    // Initialize servo states
    for (int i = 0; i < SERVO_COUNT; i++)
    {
        servos[i].currentAngle = 0;
        servos[i].targetAngle = 0;
        servos[i].speed = 1;
        servos[i].moving = false;

        writeServo(i, 0);
    }

    servoQueue = xQueueCreate(20, sizeof(ServoCommand));

    xTaskCreate(
        servoTask,
        "ServoTask",
        4096,
        nullptr,
        2,
        &servoTaskHandle);

    Serial.println();
    Serial.println("Servo Manager Started.");
    printHelp();
}

// ============================================================
// Main Loop
// ============================================================

void loop()
{
    while (Serial.available())
    {
        char c = Serial.read();

        if (c == '\n' || c == '\r')
        {
            if (serialBuffer.length())
            {
                parseCommand(serialBuffer);
                serialBuffer = "";
            }
        }
        else
        {
            serialBuffer += c;
        }
    }

    // Other work can be done here:
    //
    // - Read sensors
    // - BLE
    // - WiFi
    // - CAN
    // - IMU
    // - EMG
    // - etc.

    delay(1);
}
#include <Arduino.h>
#include "ServoController.h"

// ============================================================
// Setup
// ============================================================

void setup()
{
    Serial.begin(115200);

    randomSeed(analogRead(0));

    delay(1000);

    ServoController::Init();
}

// ============================================================
// Main Loop
// ============================================================

String input;
void loop() 
{
  while (Serial.available()) 
  {
    char c = Serial.read();

    if (c == '\n') 
    {
      ServoController::ParseCommand(input);
      input = "";
    }
    else 
    {
      input += c;
    }
  }

  delay(1);
}
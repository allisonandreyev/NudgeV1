#include <Wire.h>

#define SDA_PIN 22
#define SCL_PIN 23

void setup() {
  Serial.begin(115200);
  delay(1000);

  Wire.begin(SDA_PIN, SCL_PIN);

  Serial.println("Starting scan");

  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    byte error = Wire.endTransmission();

    if (error == 0) {
      Serial.printf("Found: 0x%02X\n", addr);
    }
  }

  Serial.println("Scan complete");
}

void loop() {
  delay(1000);

  Serial.println("Starting scan");

  for (byte addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    byte error = Wire.endTransmission();

    if (error == 0) {
      Serial.printf("Found: 0x%02X\n", addr);
    }
  }

  Serial.println("Scan complete");
}
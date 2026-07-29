#include <Wire.h>

#define SDA_PIN 22
#define SCL_PIN 23

void setup() {
  Serial.begin(115200);
  delay(1000);

  Serial.println("\nI2C Scanner");

  Wire.begin(SDA_PIN, SCL_PIN);

  Serial.println("Scanning...");

  byte count = 0;

  for (byte address = 1; address < 127; address++) {
    Wire.beginTransmission(address);

    byte error = Wire.endTransmission();

    if (error == 0) {
      Serial.print("Found device at 0x");
      if (address < 16) Serial.print("0");
      Serial.println(address, HEX);

      count++;
    }
  }

  if (count == 0) {
    Serial.println("No I2C devices found!");
  }
  else {
    Serial.println("Scan complete.");
  }
}

void loop() {
}
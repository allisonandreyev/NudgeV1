#include <Wire.h>

void setup() {
  Serial.begin(115200);

  Wire.begin(22, 23);

  delay(1000);

  Serial.println("Writing MODE1 reset");

  Wire.beginTransmission(0x40);
  Wire.write(0x00);
  Wire.write(0x00);

  byte result = Wire.endTransmission();

  Serial.print("Result: ");
  Serial.println(result);
}

void loop() {}
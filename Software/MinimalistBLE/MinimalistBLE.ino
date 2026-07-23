#pragma once 
#include <Arduino.h>
#include <NimBLEDevice.h>
#include "./BLE.h"

BLE ble;
void setup(void)
{
  Serial.begin(115200);

  // Set up BLE, advertising, a service, and a characteristic, then begin advertising them all
  ble.Init();
  ble.AddService("DataService", "12345678-1234-1234-1234-123456789ABC");
  ble.AddCharacteristic("DataService", "RandomData", "87654321-4321-4321-4321-CBA987654321", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  ble.StartService("DataService");
  ble.StartAdvertising();
}

void loop() {
  // Rate limiter to not burn out the clock chip
  delay(250);

  // Generate random data and store it for transmission as a human readable string
  uint8_t randvar = random(0, 100);
  std::string value = std::to_string(randvar);
  ble.SetValue("RandomData", value.c_str());

  // Send all characteristics if a client is connected
  if(ble.UpdateClients())
    Serial.printf("Successfully sent data '%d'.\n", randvar);
}
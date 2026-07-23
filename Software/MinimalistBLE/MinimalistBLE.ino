#pragma once 
#include <Arduino.h>
#include <NimBLEDevice.h>
#include "./BLE.h"

BLE ble;
void setup(void)
{
  Serial.begin(115200);
  ble.Init();
  ble.AddService("DataService", "12345678-1234-1234-1234-123456789ABC");
  ble.AddCharacteristic("DataService", "RandomData", "87654321-4321-4321-4321-CBA987654321", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  ble.StartService("DataService");
  ble.StartAdvertising();
}

void loop() {
  delay(250);
  uint8_t randvar = random(0, 100);
  std::string value = std::to_string(randvar);
  ble.SetValue("RandomData", value.c_str());

  if(ble.UpdateClients())
    Serial.printf("Successfully sent data '%d'.\n", randvar);
  //Serial.printf("Connected clients: %d\n", pServer->getConnectedCount());
}
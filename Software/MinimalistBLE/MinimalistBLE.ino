#pragma once 
#include <Arduino.h>
#include <NimBLEDevice.h>
#include "./BLE.h"

BLE ble;
void setup(void)
{
  Serial.begin(115200);
  ble.Init();
}

void loop() {
  delay(2000);
  ble.UpdateClients();
  //Serial.printf("Connected clients: %d\n", pServer->getConnectedCount());
}
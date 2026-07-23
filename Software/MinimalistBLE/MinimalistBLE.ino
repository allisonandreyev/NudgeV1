#pragma once 
#include <Arduino.h>
#include <NimBLEDevice.h>
#include "./BLE.h"
#include "./BLECallbacks.h"

BLE ble;
void setup(void)
{
  Serial.begin(115200);

  // Set up BLE, advertising, a service, and a characteristic, then begin advertising them all
  ble.Init();
  ble.AddService("DataService", "12345678-1234-1234-1234-123456789ABC");
  
  // Add TX (RandomData) and RX (PhoneCmdLine) characteristics
  ble.AddCharacteristic("DataService", "RandomData", "87654321-4321-4321-4321-CBA987654321", NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  ble.AddCharacteristic("DataService", "PhoneCmdLine", "99999999-5555-4321-4321-CBA987654321", NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  
  // Add an onWrite (when data is received from phone) callback
  ble.SetCallbacks("PhoneCmdLine", 
  {
    .onWrite = [](auto* c, auto& info)
    {
      std::string message = c->getValue();

      Serial.print("Received: ");
      Serial.println(message.c_str());
    }
  });

  // Start required services
  ble.StartService("DataService");
  ble.StartAdvertising();
}

void loop() {
  // Rate limiter to not burn out the clock chip
  delay(2000);

  // Generate random data and store it for transmission as a human readable string
  uint8_t randvar = random(0, 100);
  std::string value = std::to_string(randvar);
  ble.SetValue("RandomData", value.c_str());

  // Send all characteristics if a client is connected
  if(ble.UpdateClients())
    Serial.printf("Successfully sent data '%d'.\n", randvar);
}
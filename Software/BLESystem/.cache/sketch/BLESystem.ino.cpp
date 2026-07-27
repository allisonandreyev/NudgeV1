#line 1 "C:\\Users\\veile\\Documents\\CPSE\\NudgeV1\\Software\\BLESystem\\BLESystem.ino"
#include <Arduino.h>
#include <NimBLEDevice.h>
#include "./BLE.h"
#include "./BLECallbacks.h"
#include "./Packet.h"

BLE ble;

#line 9 "C:\\Users\\veile\\Documents\\CPSE\\NudgeV1\\Software\\BLESystem\\BLESystem.ino"
void setup(void);
#line 39 "C:\\Users\\veile\\Documents\\CPSE\\NudgeV1\\Software\\BLESystem\\BLESystem.ino"
void loop();
#line 9 "C:\\Users\\veile\\Documents\\CPSE\\NudgeV1\\Software\\BLESystem\\BLESystem.ino"
void setup(void)
{
  Serial.println("TESTING");
  ble.Init("Advanced Packet v0.3.2");

  // Structure: BLE 5.3-DATA-MOVE-DEST-[UUID] (typically encoded in ascii)
  ble.AddService("TX", "000B1E53-D47A-CEDE-DE57-000000008488");
  ble.AddService("RX", "000B1E53-D47A-CEDE-DE57-000000008288");

  // Structure: [Last 8 of Service UUID]-DATA-MOVE-0000-[UUID] (typically encoded in ascii)
  ble.AddCharacteristic("TX", "SendEMGData", "00008488-D47A-CEDE-0000-466178454d47", PROP::READ | PROP::NOTIFY); // FaxEMG
  ble.AddCharacteristic("RX", "RecieveCmds", "00008288-D47A-CEDE-0000-526563436d64", PROP::WRITE | PROP::WRITE_NR); //RecCmd

  // ble.SetCallbacks("RX",
  // {
  //   .onWrite = [](auto* c, auto& info)
  //   {
  //     std::string message = c->getValue();

  //     Serial.print("Received: ");
  //     Serial.println(message.c_str());
  //   }
  // });

  ble.StartService("TX");
  ble.StartService("RX");
  ble.StartAdvertising();
}

uint8_t c = 0;
void loop()
{
  delay(500);
  Packet p;
  uint32_t randvar = random(5000, 328515105);
  uint32_t randvar2 = random(5000, 328515105);
  p.Append(randvar);
  p.Append(randvar2);
  p.SetFlags(0);
  p.SetMessageID(c);
  p.SetVersion(1);
  
  // std::string value = std::to_string(randvar);
  // ble.SetValue("SendEMGData", value.c_str());
  ble.SetValue("SendEMGData", p.Serialize());
  ble.UpdateClients();

  Serial.println("TESTING 2");

  p.ClearData();
  c++;
}

#include "includes.h"

BLE ble;

void setup()
{
  Serial.begin(115200);
  ServoController::Init();
  ServoController::SetServo(8, 180, 35);

  ble.Init("Nudge Prototype 0.0.1");

  // Structure: BLE 5.3-DATA-MOVE-DEST-[UUID] (typically encoded in ascii)
  ble.AddService("TX", "000B1E53-D47A-CEDE-DE57-000000008488");
  ble.AddService("RX", "000B1E53-D47A-CEDE-DE57-000000008288");

  // Structure: [Last 8 of Service UUID]-DATA-MOVE-0000-[UUID] (typically encoded in ascii)
  ble.AddCharacteristic("TX", "SendEMGData", "00008488-D47A-CEDE-0000-466178454d47", PROP::READ | PROP::NOTIFY); // FaxEMG
  ble.AddCharacteristic("RX", "ReceiveCmds", "00008288-D47A-CEDE-0000-526563436d64", PROP::WRITE | PROP::WRITE_NR); //RecCmd

  ble.SetCallbacks("ReceiveCmds",
  {
    .onWrite = [](auto* c, auto& info)
    {
      std::string message = c->getValue();

      Serial.print("Received: ");
      Serial.println(message.c_str());
    }
  });

  ble.StartService("TX");
  ble.StartService("RX");
  ble.StartAdvertising();
}

uint8_t c = 0;
void loop()
{
  // Packet p;

  // p.SetFlags(0);
  // p.SetMessageID(c++);
  // p.SetVersion(2);

  // p.Append(NULL);

  // auto packetQueue = p.Serialize();
  // while(!packetQueue.empty() && ble.ClientConnected())
  // {
  //   auto packet = packetQueue.front();
  
  //   Serial.printf("Sending %u bytes\r\n", packet.size());
  //   Serial.printf( "Sending segment %d/%d size=%u\n", (packet[3] & 0x0F) + 1, packet[3] >> 4, packet.size());
  
  //   ble.SetValue("SendEMGData", packet);
  //   ble.UpdateClients();
  
  //   // PrintHex(packet);
  //   delay(25);
  //   packetQueue.erase(packetQueue.begin());
  // }

  // p.ClearData();
}

/*
ble.Init("Advanced Packet v0.8.4");

  // Structure: BLE 5.3-DATA-MOVE-DEST-[UUID] (typically encoded in ascii)
  ble.AddService("TX", "000B1E53-D47A-CEDE-DE57-000000008488");
  ble.AddService("RX", "000B1E53-D47A-CEDE-DE57-000000008288");

  // Structure: [Last 8 of Service UUID]-DATA-MOVE-0000-[UUID] (typically encoded in ascii)
  ble.AddCharacteristic("TX", "SendEMGData", "00008488-D47A-CEDE-0000-466178454d47", PROP::READ | PROP::NOTIFY); // FaxEMG
  ble.AddCharacteristic("RX", "ReceiveCmds", "00008288-D47A-CEDE-0000-526563436d64", PROP::WRITE | PROP::WRITE_NR); //RecCmd

  ble.SetCallbacks("RX",
  {
    .onWrite = [](auto* c, auto& info)
    {
      std::string message = c->getValue();

      Serial.print("Received: ");
      Serial.println(message.c_str());
    }
  });

  ble.StartService("TX");
  ble.StartService("RX");
  ble.StartAdvertising();*/

  /*
  Packet p;
  
  p.SetFlags(0);
  p.SetMessageID(c++);
  p.SetVersion(2);

  int8_t i8 = -42;
  int16_t i16 = -32000;
  int32_t i32 = -2000000;
  int64_t i64 = -9000000000;
  uint8_t ui8 = 250;
  uint16_t ui16 = 60000;
  uint32_t ui32 = 4000000000;
  uint64_t ui64 = 900000000000;
  bool boolean = true;
  float floating = 3.14159f;
  double doub = 123.456;
  std::string text = "Hello from ESP32";

  for(int i = 0; i < 1; i++)
  {
    p.Append(i8);
    p.Append(i16);
    p.Append(i32);
    p.Append(i64);

    p.Append(ui8);
    p.Append(ui16);
    p.Append(ui32);
    p.Append(ui64);

    p.Append(boolean);

    p.Append(floating);
    p.Append(doub);

    p.Append(text);
  }
  
  auto packetQueue = p.Serialize();
  while(!packetQueue.empty() && ble.ClientConnected())
  {
    auto packet = packetQueue.front();
    
    Serial.printf("Sending %u bytes\r\n", packet.size());
    Serial.printf( "Sending segment %d/%d size=%u\n", (packet[3] & 0x0F) + 1, packet[3] >> 4, packet.size());
    
    ble.SetValue("SendEMGData", packet);
    ble.UpdateClients();
    
    PrintHex(packet);
    delay(25);
    packetQueue.erase(packetQueue.begin());
  }

  p.ClearData();*/
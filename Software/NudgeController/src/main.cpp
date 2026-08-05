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

// uint8_t c = 0;
void loop()
{
  ServoController::SetServo(0, 260, 200);
  delay(5000);
  ServoController::SetServo(0, 0, 200);
  delay(5000);
  // Read data
  // Pass data to tinyML
  // Read tinyML response
  // Interpret tinyML response
  // Move servos

  // Read BLE cmds
  // Interpret BLE cmds
  // Move servos

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
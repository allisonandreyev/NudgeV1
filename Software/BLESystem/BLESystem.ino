#include <Arduino.h>
#include <NimBLEDevice.h>
#include "./BLE.h"
#include "./BLECallbacks.h"
#include "./Packet.h"
#include <array>

BLE ble;
std::array<uint8_t, 506> data{};

void setup(void)
{
  Serial.begin(115200);
  ble.Init("Advanced Packet v0.8.3");

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

  for(size_t i = 0; i < data.size(); i++)
  {
    data[i] = i & 0xFF;
  }
}

uint8_t c = 0;
void PrintHex(const std::vector<uint8_t>& data)
{
  Serial.print("Packet HEX: \r\n");

  for (uint8_t byte : data)
  {
    if (byte < 0x10)
      Serial.print("0"); // leading zero for single digit hex

    Serial.print(byte, HEX);
    Serial.print("-");
  }

  Serial.println();
}

int8_t a = -5;
int16_t b = -1234;
uint32_t c = 123456;
float d = 3.14159;
bool e = true;
std::string f = "Hello BLE";

void loop()
{
  delay(10);

  if (!ble.ClientConnected()) { Serial.println("No clients connected..."); return; }
  Serial.println("=============== NEW PACKET ===============");
  Packet p;
  
  // uint32_t randvar = random(5000, 328515105);
  // uint32_t randvar2 = random(5000, 328515105);
  p.SetFlags(0);
  p.SetMessageID(c++);
  p.SetVersion(2);

  // p.Append(randvar);
  // p.Append(randvar2);

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
  
  // std::string value = std::to_string(randvar);
  // ble.SetValue("SendEMGData", value.c_str());
  auto packetQueue = p.Serialize();

  while(!packetQueue.empty() && ble.ClientConnected())
  {
    auto packet = packetQueue.front();
    Serial.printf("Sending %u bytes\r\n", packet.size());
    Serial.printf(
      "Sending segment %d/%d size=%u\n",
      packet[3] & 0x0F,
      packet[3] >> 4,
      packet.size()
    );
    ble.SetValue("SendEMGData", packet);
    ble.UpdateClients();
    PrintHex(packet);
    delay(15);
    packetQueue.erase(packetQueue.begin());
  }

  ble.UpdateClients();
  p.ClearData();
}
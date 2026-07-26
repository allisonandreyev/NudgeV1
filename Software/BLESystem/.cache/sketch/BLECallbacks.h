#line 1 "C:\\Users\\veile\\Documents\\CPSE\\NudgeV1\\Software\\BLESystem\\BLECallbacks.h"
#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>
#include <unordered_map>
#include <string>
#include <functional>

/*
  Imma be real, I have no idea how most of this black magic works, but it works
*/

/* ==== Characteristic callbacks ==== */
struct BLECharacteristicCallbackConfig
{
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&)> onRead;
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&)> onWrite;
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&, uint16_t)> onSubscribe;
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&, uint16_t)> onStatus;
};

class BLECharacteristicCallbackHandler : public NimBLECharacteristicCallbacks
{
  private:
    BLECharacteristicCallbackConfig config;

  public:
    BLECharacteristicCallbackHandler(BLECharacteristicCallbackConfig cfg) : config(cfg) {}

  void onRead(NimBLECharacteristic* c, NimBLEConnInfo& info) override
    { if(config.onRead) config.onRead(c, info); }

  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override
    { if(config.onWrite) config.onWrite(c, info); }

  void onSubscribe(NimBLECharacteristic* c, NimBLEConnInfo& info, uint16_t subValue) override
    { if(config.onSubscribe) config.onSubscribe(c, info, subValue); }
  
  void onStatus(NimBLECharacteristic* c, NimBLEConnInfo& info, int code) override
    { if(config.onStatus) config.onStatus(c, info, code); }
};


/* ==== Server callbacks ==== */
class BLEServerCallbackHandler : public NimBLEServerCallbacks
{
  public:
    void onConnect(NimBLEServer*, NimBLEConnInfo&) override
    {
      Serial.println("BLE Client Connected");
    }

    void onDisconnect(NimBLEServer*, NimBLEConnInfo&, int reason) override
    {
      Serial.printf("BLE Client Disconnected (%d)\n", reason);
      NimBLEDevice::startAdvertising();
    }
};
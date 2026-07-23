#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>
#include <unordered_map>
#include <string>
#include <functional>

/*
  Imma be real, I have no idea how most of this black magic works, but it works
*/

struct BLECallbackConfig
{
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&)> onRead;
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&)> onWrite;
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&, uint16_t)> onSubscribe;
  std::function<void(NimBLECharacteristic*, NimBLEConnInfo&, uint16_t)> onStatus;
};

class BLECharacteristicCallbackHandler : public NimBLECharacteristicCallbacks
{
  private:
    BLECallbackConfig config;

  public:
    BLECharacteristicCallbackHandler(BLECallbackConfig cfg) : config(cfg) {}

  void onRead(NimBLECharacteristic* c, NimBLEConnInfo& info) override
    { if(config.onRead) config.onRead(c, info); }

  void onWrite(NimBLECharacteristic* c, NimBLEConnInfo& info) override
    { if(config.onWrite) config.onWrite(c, info); }

  void onSubscribe(NimBLECharacteristic* c, NimBLEConnInfo& info, uint16_t subValue) override
    { if(config.onSubscribe) config.onSubscribe(c, info, subValue); }
  
  void onStatus(NimBLECharacteristic* c, NimBLEConnInfo& info, int code) override
    { if(config.onStatus) config.onStatus(c, info, code); }
};
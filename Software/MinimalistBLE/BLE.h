#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>

class BLE
{
  private:
    static NimBLEServer* pServer;
    static NimBLECharacteristic* pServoPosition;
    static NimBLEService* pDataService;

  public:
    BLE();
    void Init();
    void UpdateClients();
    void AddService();
};
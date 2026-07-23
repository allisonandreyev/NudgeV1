#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>
#include <unordered_map>
#include <string>

struct ServiceInfo
{
  //std::string name;
  std::string uuid;
  NimBLEService* service;
};
struct CharacteristicInfo
{
  //std::string name;
  std::string uuid;
  NimBLECharacteristic* characteristic;
};


class BLE
{
  private:
    NimBLEServer* pServer;
    NimBLEAdvertising* pAdvertising;
    std::unordered_map<std::string, ServiceInfo> bleServices;
    std::unordered_map<std::string, CharacteristicInfo> bleCharacteristics;

  public:
    BLE();
    ~BLE()
    {
      bleCharacteristics.clear();
      bleServices.clear();
    }

    void Init();
    bool UpdateClients();
    NimBLEService* AddService(const char* name, const char* uuid);
    NimBLECharacteristic* AddCharacteristic(const char* ServiceName, const char* CharacteristicName, const char* uuid, uint32_t Properties);
    bool StartService(const char* name);
    void StartAdvertising();
    NimBLECharacteristic* GetCharacteristic(const char* uuid);

    bool SetValue(const char* name, String& data);
    bool SetValue(const char* name, const char* data);
    bool SetValue(const char* name, const uint8_t* data, size_t size);
    bool SetValue(const char* name, uint16_t data);
    template<typename T>
    bool SetValue(const char* name, const T& value)
    {
      return SetValue(name, reinterpret_cast<const uint8_t*>(&value), sizeof(T));
    }
};
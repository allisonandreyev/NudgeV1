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
    NimBLEServer* pServer; // Main BLE server. MUST BE ACTIVE TO WORK
    NimBLEAdvertising* pAdvertising; // Global advertising service pointer
    std::unordered_map<std::string, ServiceInfo> bleServices; // Storage of all services in memory
    std::unordered_map<std::string, CharacteristicInfo> bleCharacteristics; // Storage of all characteristics in memory

  public:
    // Constructor/deconstructor
    BLE();
    ~BLE()
    {
      bleCharacteristics.clear();
      bleServices.clear();
    }

    void Init(); // Initialize BLE syste,
    bool UpdateClients(); // Send all characteristics (data)
    NimBLEService* AddService(const char* name, const char* uuid); // Adds a service
    NimBLECharacteristic* AddCharacteristic(const char* ServiceName, const char* CharacteristicName, const char* uuid, uint32_t Properties); // Adds a characteristic
    bool StartService(const char* name); // Starts a service
    void StartAdvertising(); // Starts advertising BLE connections and services
    NimBLECharacteristic* GetCharacteristic(const char* name); // Get a NimBLE characteristic by name

    // Sets the value of a characteristic
    bool SetValue(const char* name, String& data);
    bool SetValue(const char* name, const char* data);
    bool SetValue(const char* name, const uint8_t* data, size_t size);
    bool SetValue(const char* name, uint16_t data);
    // Catch-all for bad data types. Sends data as raw binary bits
    template<typename T>
    bool SetValue(const char* name, const T& value)
    {
      return SetValue(name, reinterpret_cast<const uint8_t*>(&value), sizeof(T));
    }
};
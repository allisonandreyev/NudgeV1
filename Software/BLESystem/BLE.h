#pragma once
#include <Arduino.h>
#include <NimBLEDevice.h>
#include <unordered_map>
#include <string>
#include <functional>
#include "./BLECallbacks.h"
#include <vector>

// Struct to store services and characteristics in memory in a easy to reference format
struct ServiceInfo
{
  std::string uuid;
  NimBLEService* service;
};
struct CharacteristicInfo
{
  std::string uuid;
  NimBLECharacteristic* characteristic;
};
//might add std::string name to these in case I need to do comparisons or smth


namespace PROP
{
  constexpr auto READ = NIMBLE_PROPERTY::READ;
  constexpr auto READ_ENC = NIMBLE_PROPERTY::READ_ENC;
  constexpr auto READ_AUTHEN = NIMBLE_PROPERTY::READ_AUTHEN;
  constexpr auto READ_AUTHOR = NIMBLE_PROPERTY::READ_AUTHOR;
  constexpr auto WRITE = NIMBLE_PROPERTY::WRITE;
  constexpr auto WRITE_NR = NIMBLE_PROPERTY::WRITE_NR;
  constexpr auto WRITE_ENC = NIMBLE_PROPERTY::WRITE_ENC;
  constexpr auto WRITE_AUTHEN = NIMBLE_PROPERTY::WRITE_AUTHEN;
  constexpr auto WRITE_AUTHOR = NIMBLE_PROPERTY::WRITE_AUTHOR;
  constexpr auto BROADCAST = NIMBLE_PROPERTY::BROADCAST;
  constexpr auto NOTIFY = NIMBLE_PROPERTY::NOTIFY;
  constexpr auto INDICATE = NIMBLE_PROPERTY::INDICATE;
}


class BLE
{
  private:
    NimBLEServer* pServer; // Main BLE server. MUST BE ACTIVE TO WORK
    NimBLEAdvertising* pAdvertising; // Global advertising service pointer
    std::unordered_map<std::string, ServiceInfo> bleServices; // Storage of all services in memory
    std::unordered_map<std::string, CharacteristicInfo> bleCharacteristics; // Storage of all characteristics in memory
    std::vector<BLECharacteristicCallbackHandler*> Callbacks; // Keeps track of all callbacks on all characteristics
    const char* deviceName = "Unnamed NimBLE Server";

  public:
    /* ==== CONSTRUCTOR / DESTRUCTOR ==== */
    BLE();
    ~BLE()
    {
      for(auto* callback : Callbacks) // Safely destroy all callbacks
        delete callback;

      Callbacks.clear(); // Clear callbacks array

      // Clear characteristic and service arrays
      bleCharacteristics.clear();
      bleServices.clear();
    }

    /* ==== INITIALIZERS ==== */
    void Init(); // Initialize BLE system
    void Init(const char* name); // Choose the devices name
    bool UpdateClients(); // Send all characteristics (data)

    /* ==== ADDERS ==== */
    NimBLEService* AddService(const char* name, const char* uuid); // Adds a service
    NimBLECharacteristic* AddCharacteristic(const char* ServiceName, const char* CharacteristicName, const char* uuid, uint32_t Properties); // Adds a characteristic
    
    /* ==== STARTS various required subsystems ==== */
    bool StartService(const char* name); // Starts a service
    void StartAdvertising(); // Starts advertising BLE connections and services
    
    /* ==== GETTERS ==== */
    NimBLECharacteristic* GetCharacteristic(const char* name); // Get a NimBLE characteristic by name
    void SetCallbacks(const char* CharacteristicName, BLECharacteristicCallbackConfig config); // Adds a callback to a characteristic channel

    /* ==== DATA SETTERS ==== */
    bool SetValue(const char* name, String& data);
    bool SetValue(const char* name, const char* data);
    bool SetValue(const char* name, const uint8_t* data, size_t size);
    bool SetValue(const char* name, const std::vector<uint8_t>& data);
    bool SetValue(const char* name, uint16_t data);
    template<typename T> /* Catch-all for unsupported data types. Sends data as raw binary bits */
    bool SetValue(const char* name, const T& value)
    {
      return SetValue(name, reinterpret_cast<const uint8_t*>(&value), sizeof(T));
    }
};
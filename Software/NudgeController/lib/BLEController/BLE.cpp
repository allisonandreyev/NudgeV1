#include "./BLE.h"
#include <NimBLEDevice.h>
#include <unordered_map>
#include <string>
#include "./BLECallbacks.h"

uint16_t BLE::MTU = 517;

// Create BLE object. Ensure that all major pointers are at least initialized as null
BLE::BLE() : pServer(nullptr), pAdvertising(nullptr), serverCallbacks(nullptr)
{
  Serial.println("BLE object created.");
}

// Initialize the BLE server
void BLE::Init()
{
  Init(deviceName); // Uses default name defined in .h
}
void BLE::Init(const char* name)
{
  deviceName = name;

  // Get board UUID
  uint64_t chipid = ESP.getEfuseMac();
  Serial.printf("Board UUID: %04X%08X\r\n", (uint16_t)(chipid >> 32), (uint32_t)chipid);

  // Initialize NimBLE
  NimBLEDevice::init(deviceName);

  // Set the highest MTU we can support
  NimBLEDevice::setMTU(517);

  // Create the server and set the device name
  pServer = NimBLEDevice::createServer();
  serverCallbacks = new BLEServerCallbackHandler();
  pServer->setCallbacks(serverCallbacks);
  Serial.printf("Beginning NimBLE Server\r\n");
  
  // Set the advertising pointer and give it a broadcasted name
  pAdvertising = NimBLEDevice::getAdvertising();
  pAdvertising->setName(deviceName);
  pAdvertising->enableScanResponse(true);
}

// Update all characteristics that are currently active in the GATT database
bool BLE::UpdateClients()
{
  // Check if a client is connected
  // if (!pServer->getConnectedCount()) { Serial.println("No clients connected..."); return false; }

  // Loop through all characteristics and send their data
  for(auto& it : bleCharacteristics)
  {
    it.second.characteristic->notify();
  }

  return true;
}

// Adds a service to the GATT database and advertising service
NimBLEService* BLE::AddService(const char* name, const char* uuid)
{
  // Ensure the server is initialized
  if (!pServer)
  {
    Serial.println("BLE not initialized.");
    return nullptr;
  }

  // Ensure that the service does not already exist
  if (bleServices.count(name))
  {
    Serial.println("Service already exists.");
    return bleServices[name].service;
  }

  // Create service in the GATT database and ensure it succeeds
  auto* service = pServer->createService(uuid);
  if(!service) { Serial.println("Failed to create service."); return nullptr; }

  // Add the service to memory with a name reference
  bleServices[name] = { uuid, service };

  // Add the service to the advertising list
  pAdvertising->addServiceUUID(service->getUUID());
  return service;
}

// Starts a service
bool BLE::StartService(const char* name)
{
  // Look for the service and ensure that it exists
  auto it = bleServices.find(name);
  if (it == bleServices.end())
      return false;

  // Start the service
  // it->second.service->start();  
  Serial.printf("Service '%s' successfully started.\r\n", name);
  return true;
}

// Add a characteristic to a service and the GATT database
NimBLECharacteristic* BLE::AddCharacteristic(const char* ServiceName, const char* CharacteristicName, const char* uuid, uint32_t Properties)
{
  // Ensure the server is initialized
  if (!pServer)
  {
    Serial.println("BLE not initialized.");
    return nullptr;
  }
  
  // Ensure that the target service exists
  auto service = bleServices.find(ServiceName);
  if(service == bleServices.end())
    return nullptr;

  // Ensure that the characteristic does not already exist
  if (bleCharacteristics.count(CharacteristicName))
  {
    Serial.println("Characteristic already exists.");
    return bleCharacteristics[CharacteristicName].characteristic;
  }

  /*
    NimBLE Property List:
      - NIMBLE_PROPERTY::READ
      - NIMBLE_PROPERTY::READ_ENC
      - NIMBLE_PROPERTY::READ_AUTHEN
      - NIMBLE_PROPERTY::READ_AUTHOR
      - NIMBLE_PROPERTY::WRITE
      - NIMBLE_PROPERTY::WRITE_NR
      - NIMBLE_PROPERTY::WRITE_ENC
      - NIMBLE_PROPERTY::WRITE_AUTHEN
      - NIMBLE_PROPERTY::WRITE_AUTHOR
      - NIMBLE_PROPERTY::BROADCAST
      - NIMBLE_PROPERTY::NOTIFY
      - NIMBLE_PROPERTY::INDICATE
    DEFAULT:
      - NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::WRITE
  */
  // Create the characteristic on a service in the GATT and ensure that it was created successfully
  auto* newCharacteristic = service->second.service->createCharacteristic(uuid, Properties);
  if(!newCharacteristic) { Serial.println("Failed to create characteristic."); return nullptr; }

  // Add the characteristic to memory with name reeference
  bleCharacteristics[CharacteristicName] = { uuid, newCharacteristic };
  return newCharacteristic;
}

// Start the advertising service
void BLE::StartAdvertising()
{
  // Ensure that the advertising service and main server exist, then start advertising
  if(!pAdvertising) { Serial.println("Advertising unavailable"); return; }
  pAdvertising->start();
  Serial.printf("Advertising Started\r\n");
}

// Returns a characteristic
NimBLECharacteristic* BLE::GetCharacteristic(const char* name)
{
  // Check for the characteristic in memory
  auto it = bleCharacteristics.find(name);

  // If the service or characteristic is no longer active, fail out ("deleted" characteristic)
  if (it == bleCharacteristics.end())
  {
    Serial.printf("Cannot find characteristic '%s'.\r\n", name);
    return nullptr;
  }

  // Return the found characteristic
  return it->second.characteristic;
}

/*  === Sets the value of a characteristic. There are multiple different datatypes, be careful which you use ===  */
bool BLE::SetValue(const char* name, String& data) // Sends a string
{ 
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data);
  return true; 
}
bool BLE::SetValue(const char* name, const char* data) // Sends a character array (another type of string)
{
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data);
  return true; 
}
bool BLE::SetValue(const char* name, const uint8_t* data, size_t size) // Sends a binary value (will be interpreted as hex)
{ 
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data, size);
  return true; 
}
bool BLE::SetValue(const char* name, const std::vector<uint8_t>& data)
{
  return SetValue(name, data.data(), data.size());
}
bool BLE::SetValue(const char* name, uint16_t data) // Sends an integer
{ 
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data);
  return true; 
}

// Adds a callback for any function to a NimBLE characteristic. It works, don't touch it
void BLE::SetCallbacks(const char* CharacteristicName, BLECharacteristicCallbackConfig config)
{
  // Ensure the characteristic exists
  auto* characteristic = GetCharacteristic(CharacteristicName);
  if(!characteristic)
  {
    Serial.println("Characteristic not found.");
    return;
  }

  // Create the callback object and add it to memory
  auto* callback = new BLECharacteristicCallbackHandler(config);
  Callbacks.push_back(callback);

  // Add the callback to the characteristic
  characteristic->setCallbacks(callback);
}

// onConnect (NimBLEServer *pServer, NimBLEConnInfo &connInfo)
// onDisconnect (NimBLEServer *pServer, NimBLEConnInfo &connInfo, int reason)

// Random stuff I'm keeping for reference if I ever get confused
//uint16_t position = random(0, 271);
//pServoPosition->setValue((uint8_t *)&position, sizeof(position));
// String position = String(random(0, 271));
// pServoPosition->setValue(position.c_str());
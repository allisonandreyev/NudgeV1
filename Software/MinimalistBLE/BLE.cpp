#include "./BLE.h"
#include <NimBLEDevice.h>
#include <unordered_map>
#include <string>

BLE::BLE() : pServer(nullptr), pAdvertising(nullptr)
{
  Serial.println("BLE object created.");
}

void BLE::Init()
{
  // Get UUID
  uint64_t chipid = ESP.getEfuseMac();
  Serial.printf("Board UUID: %04X%08X\n", (uint16_t)(chipid >> 32), (uint32_t)chipid);

   /** Initialize NimBLE and set the device name */
  NimBLEDevice::init("Nudge Arm");
  pServer = NimBLEDevice::createServer();
  Serial.printf("Beginning NimBLE Server\n");
  
  /** Create an advertising instance and add the services to the advertised data */
  pAdvertising = NimBLEDevice::getAdvertising();
  pAdvertising->setName("Nudge Arm");
  pAdvertising->enableScanResponse(true);
}

bool BLE::UpdateClients()
{
  if (!pServer->getConnectedCount()) { Serial.println("No clients connected..."); return false; }
  // Serial.println("Client connected. 'Sending' data (not really, I\'m lying).");

  for(auto& it : bleCharacteristics)
  {
    it.second.characteristic->notify();
  }

  return true;
}

NimBLEService* BLE::AddService(const char* name, const char* uuid)
{
  if (!pServer)
  {
    Serial.println("BLE not initialized.");
    return nullptr;
  }

  if (bleServices.count(name))
  {
    Serial.println("Service already exists.");
    return bleServices[name].service;
  }

  auto* service = pServer->createService(uuid);

  if(!service) { Serial.println("Failed to create service."); return nullptr; }

  bleServices[name] = { uuid, service };

  pAdvertising->addServiceUUID(service->getUUID());
  return service;
}

bool BLE::StartService(const char* name)
{
  auto it = bleServices.find(name);

  if (it == bleServices.end())
      return false;

  it->second.service->start();
  Serial.printf("Service '%s' successfully started.\n", name);
  return true;
}

NimBLECharacteristic* BLE::AddCharacteristic(const char* ServiceName, const char* CharacteristicName, const char* uuid, uint32_t Properties)
{
  if (!pServer)
  {
    Serial.println("BLE not initialized.");
    return nullptr;
  }
  
  auto service = bleServices.find(ServiceName);
  if(service == bleServices.end())
    return nullptr;

  if (bleCharacteristics.count(CharacteristicName))
  {
    Serial.println("Characteristic already exists.");
    return bleCharacteristics[CharacteristicName].characteristic;
  }

  // the only properties are )strangely) NIMBLE_PROPERTY::READ and NIMBLE_PROPERTY::WRITE. I expected more
  auto* newCharacteristic = service->second.service->createCharacteristic(uuid, Properties);

  if(!newCharacteristic) { Serial.println("Failed to create characteristic."); return nullptr; }

  bleCharacteristics[CharacteristicName] = { uuid, newCharacteristic };

  return newCharacteristic;
}

void BLE::StartAdvertising()
{
  if(!pAdvertising) { Serial.println("Advertising unavailable"); return; }
  pAdvertising->start();
  Serial.printf("Advertising Started\n");
}

NimBLECharacteristic* BLE::GetCharacteristic(const char* name)
{
    auto it = bleCharacteristics.find(name);

    if (it == bleCharacteristics.end())
        return nullptr;

    return it->second.characteristic;
}

// void BLE::SetValue(NimBLECharacteristic* characteristic, const uint8_t* data, size_t size)
// {
//   characteristic->setValue(data, size);
// }

bool BLE::SetValue(const char* name, String& data) 
{ 
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data);
  return true; 
}
bool BLE::SetValue(const char* name, const char* data) 
{
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data);
  return true; 
}
bool BLE::SetValue(const char* name, const uint8_t* data, size_t size) 
{ 
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data, size);
  return true; 
}
bool BLE::SetValue(const char* name, uint16_t data) 
{ 
  auto* c = GetCharacteristic(name);
  if(!c) return false;
  
  c->setValue(data);
  return true; 
}


/*
void BLE::StartAllServices()
{
  for(auto& service : bleServices)
  {
    service.second->start();
  }
}
*/

// bool Notify(const char* uuid, const uint8* data, size_t length);

  /*Set up data sending service and data channel*/
  // pDataService = pServer->createService("12345678-1234-1234-1234-123456789ABC");

  // pServoPosition = pDataService->createCharacteristic(
  //         "87654321-4321-4321-4321-CBA987654321",
  //         NIMBLE_PROPERTY::READ |
  //         NIMBLE_PROPERTY::NOTIFY
  //     );

  // pDataService->start();


      //uint16_t position = random(0, 271);
    //pServoPosition->setValue((uint8_t *)&position, sizeof(position));

    // String position = String(random(0, 271));
    // pServoPosition->setValue(position.c_str());

    // bool success = pServoPosition->notify();
    // Serial.printf("Sent %s  notify=%d\n", position.c_str(), success);


  // pAdvertising->addServiceUUID(pDataService->getUUID());

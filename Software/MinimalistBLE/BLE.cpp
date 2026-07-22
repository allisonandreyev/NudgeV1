#include "./BLE.h"
#include <NimBLEDevice.h>

NimBLEServer* BLE::pServer = nullptr;
NimBLECharacteristic* BLE::pServoPosition = nullptr;
NimBLEService* BLE::pDataService = nullptr;

BLE::BLE()
{
  
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
  
  /*Set up data sending service and data channel*/
  pDataService = pServer->createService("12345678-1234-1234-1234-123456789ABC");

  pServoPosition = pDataService->createCharacteristic(
          "87654321-4321-4321-4321-CBA987654321",
          NIMBLE_PROPERTY::READ |
          NIMBLE_PROPERTY::NOTIFY
      );

  pDataService->start();


  /** Create an advertising instance and add the services to the advertised data */
  NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
  pAdvertising->setName("Nudge Arm");
  pAdvertising->enableScanResponse(true);

  /*Add all services to ensure phones can read it*/
  pAdvertising->addServiceUUID(pDataService->getUUID());

  pAdvertising->start();
  Serial.printf("Advertising Started\n");
}

void BLE::UpdateClients()
{
  if (pServer->getConnectedCount()) {
    //uint16_t position = random(0, 271);
    //pServoPosition->setValue((uint8_t *)&position, sizeof(position));

    String position = String(random(0, 271));
    pServoPosition->setValue(position.c_str());

    bool success = pServoPosition->notify();
    Serial.printf("Sent %s  notify=%d\n", position.c_str(), success);
  }
}

void BLE::AddService()
{

}
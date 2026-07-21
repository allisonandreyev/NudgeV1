#define ACTIVE_SKETCH
#if ACTIVE_SKETCH == 1
#include <Arduino.h>
#include <NimBLEDevice.h>

static NimBLEServer* pServer

void setup(void)
{
  Serial.begin(115200);

  // Get UUID
  uint64_t chipid = ESP.getEfuseMac();
  Serial.printf("Board UUID: %04X%08X\n",
                  (uint16_t)(chipid >> 32),
                  (uint32_t)chipid);
                  
  Serial.printf("Beginning NimBLE Server\n");

  /** Initialize NimBLE and set the device name */
  NimBLEDevice::init("Nudge Arm");

  /** Create an advertising instance and add the services to the advertised data */
  NimBLEAdvertising* pAdvertising = NimBLEDevice::getAdvertising();
  pAdvertising->setName("Nudge Arm " + );

  pAdvertising->enableScanResponse(true);
  pAdvertising->start();

  Serial.printf("Advertising Started\n");
}

void loop()
{
  /** Loop here and send notifications to connected peers */
  delay(2000);
  if (pServer->getConnectedCount()) {
    // NimBLEService* pSvc = pServer->getServiceByUUID("BAAD");
    // if (pSvc) {
    //     NimBLECharacteristic* pChr = pSvc->getCharacteristic("F00D");
    //     if (pChr) {
    //         pChr->notify();
    //     }
    // }
  }
}
#endif
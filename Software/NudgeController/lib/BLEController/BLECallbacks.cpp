#include "BLECallbacks.h"
#include "BLE.h"

void BLEServerCallbackHandler::onConnect(NimBLEServer*, NimBLEConnInfo& info)
{
    Serial.printf("BLE Client Connected. Initial MTU: %d.\r\n", info.getMTU());
}

void BLEServerCallbackHandler::onDisconnect(NimBLEServer*, NimBLEConnInfo&, int reason)
{
    Serial.printf("BLE Client Disconnected (%d)\r\n", reason);
    NimBLEDevice::startAdvertising();
}

void BLEServerCallbackHandler::onMTUChange(uint16_t mtu, NimBLEConnInfo& connInfo)
{
    Serial.printf("MTU negotiated to: %u\r\n", mtu);
    BLE::SetMTU(mtu);
}
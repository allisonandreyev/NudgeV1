/**
 * Nudge Wearable Firmware - XIAO ESP32-C6
 * Triple EMG Sensor Stream (D0, D1, D2)
 * Using Custom Binary TLV Protocol
 *
 * Target: XIAO ESP32-C6
 * Pins: D0 (Sensor 1), D1 (Sensor 2), D2 (Sensor 3)
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>

// UUIDs - MUST MATCH ANDROID APP
#define SERVICE_UUID        "000B1E53-D47A-CEDE-DE57-000000008488"
#define CHAR_TX_UUID        "00008488-D47A-CEDE-0000-466178454D47" // For Sending Data
#define CHAR_RX_UUID        "00008288-D47A-CEDE-0000-526563436D64" // For Receiving Commands

BLEServer* pServer = NULL;
BLECharacteristic* pTxCharacteristic = NULL;
bool deviceConnected = false;
uint8_t messageId = 0;

// TLV Type Codes from App's Packet.kt
const uint16_t TYPE_FLOAT = 0x1130;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
      Serial.println(">> App Connected");
    };
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      Serial.println(">> App Disconnected");
      BLEDevice::startAdvertising();
    }
};

// Catch commands from the App
class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      String value = pCharacteristic->getValue();
      if (value.length() > 0) {
        Serial.print("Command Received: ");
        for (int i = 0; i < value.length(); i++) Serial.print(value[i]);
        Serial.println();
      }
    }
};

void setup() {
  Serial.begin(115200);
  analogReadResolution(12); // XIAO C6 is 12-bit (0-4095)

  BLEDevice::init("Nudge-C6");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // TX Characteristic (Notify) - Data stream to App
  pTxCharacteristic = pService->createCharacteristic(
                        CHAR_TX_UUID,
                        BLECharacteristic::PROPERTY_NOTIFY
                      );
  pTxCharacteristic->addDescriptor(new BLE2902());

  // RX Characteristic (Write) - Command stream from App
  BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
                                         CHAR_RX_UUID,
                                         BLECharacteristic::PROPERTY_WRITE
                                       );
  pRxCharacteristic->setCallbacks(new MyCallbacks());

  pService->start();
  BLEDevice::getAdvertising()->addServiceUUID(SERVICE_UUID);
  pServer->getAdvertising()->start();
  Serial.println(">> Nudge C6 Ready. Waiting for App...");
}

void loop() {
  if (deviceConnected) {
    // 1. Read Sensors
    float emg0 = analogRead(D0);
    float emg1 = analogRead(D1);
    float emg2 = analogRead(D2);

    /**
     * 2. Build Custom TLV Packet
     * Header (6 bytes) + 3 TLV Chunks (8 bytes each) = 30 bytes total
     */
    uint8_t packet[30];

    // HEADER
    packet[0] = 0x01;       // Version
    packet[1] = 0x00;       // Flags
    packet[2] = messageId++; // Msg ID
    packet[3] = 0x10;       // Segments (High nibble: 1 total, Low nibble: 0 current)
    packet[4] = 0x00;       // Payload Length High Byte
    packet[5] = 24;         // Payload Length Low Byte (3 sensors * 8 bytes)

    int offset = 6;

    // SENSOR D0 CHUNK (TLV)
    packet[offset++] = (TYPE_FLOAT >> 8) & 0xFF;
    packet[offset++] = TYPE_FLOAT & 0xFF;             // Type: Float (0x1130)
    packet[offset++] = 0x00; packet[offset++] = 0x04; // Length: 4 bytes
    memcpy(&packet[offset], &emg0, 4); offset += 4;   // Value

    // SENSOR D1 CHUNK (TLV)
    packet[offset++] = (TYPE_FLOAT >> 8) & 0xFF;
    packet[offset++] = TYPE_FLOAT & 0xFF;             // Type: Float
    packet[offset++] = 0x00; packet[offset++] = 0x04; // Length: 4 bytes
    memcpy(&packet[offset], &emg1, 4); offset += 4;

    // SENSOR D2 CHUNK (TLV)
    packet[offset++] = (TYPE_FLOAT >> 8) & 0xFF;
    packet[offset++] = TYPE_FLOAT & 0xFF;             // Type: Float
    packet[offset++] = 0x00; packet[offset++] = 0x04; // Length: 4 bytes
    memcpy(&packet[offset], &emg2, 4); offset += 4;

    // 3. Send to App
    pTxCharacteristic->setValue(packet, 30);
    pTxCharacteristic->notify();

    delay(20); // 50Hz Transmission
  }
}

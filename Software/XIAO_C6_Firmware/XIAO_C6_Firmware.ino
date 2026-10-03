/**
 * Nudge Wearable Firmware - XIAO ESP32-C6
 * Triple EMG Sensor Stream (D0, D1, D2)
 * On-device gesture model trained by the app (see GestureModel.h)
 * Remote Servo Control (0-5)
 * Using Custom Binary TLV Protocol
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include "ServoController.h"
#include "GestureModel.h"

// UUIDs - MUST MATCH ANDROID APP
#define SERVICE_UUID        "000B1E53-D47A-CEDE-DE57-000000008488"
#define CHAR_TX_UUID        "00008488-D47A-CEDE-0000-466178454D47"
#define CHAR_RX_UUID        "00008288-D47A-CEDE-0000-526563436D64"

BLEServer* pServer = NULL;
BLECharacteristic* pTxCharacteristic = NULL;
bool deviceConnected = false;
uint8_t messageId = 0;

const uint16_t TYPE_FLOAT = 0x1130;
const uint16_t TYPE_INT16 = 0x1112;
const uint16_t TYPE_UINT16 = 0x1116;

// --- AI SMOOTHING ---
const float CONFIDENCE_REQUIRED = 0.80f;
const int CLASSIFICATION_STABILITY_REQUIRED = 3;
int classificationCounter = 0;
int lastRawClassification = -1;
int confirmedClassification = -1;
int actuatedClassification = -1;

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println(">> App Connected");
    };
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        // Never keep moving the hand on AI output once the app is gone
        ServoController::EnableAIActuation(false);
        Serial.println(">> App Disconnected");
        BLEDevice::startAdvertising();
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String value = pCharacteristic->getValue();
        if (value.length() > 0) {
            if (GestureModel::HandleCommand(value)) return;
            Serial.print("Command Received: ");
            Serial.println(value);
            ServoController::ParseCommand(value);
        }
    }
};

void setup() {
    Serial.begin(115200);
    analogReadResolution(12);
    Wire.begin();
    ServoController::Init();
    GestureModel::Init();

    /**
     * SAFETY BOOT SEQUENCE
     * 1. Disengage PTO (0) -> Wait 2s
     * 2. Home all servos (0) -> Wait 2s
     * 3. Engage PTO (30)
     */
    Serial.println(">> Starting Safety Boot Sequence...");
    ServoController::DisengagePTO(100);
    delay(2000);

    for(int i=1; i<6; i++) {
        ServoController::SetServo(i, 0, 100);
    }
    delay(2000);

    ServoController::EngagePTO(100);
    Serial.println(">> Boot Sequence Complete.");

    BLEDevice::init("Nudge-C6");
    // Room for model upload lines; the app keeps each write under 180 bytes
    BLEDevice::setMTU(247);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());

    BLEService *pService = pServer->createService(SERVICE_UUID);

    pTxCharacteristic = pService->createCharacteristic(
            CHAR_TX_UUID,
            BLECharacteristic::PROPERTY_NOTIFY
    );
    pTxCharacteristic->addDescriptor(new BLE2902());

    BLECharacteristic *pRxCharacteristic = pService->createCharacteristic(
            CHAR_RX_UUID,
            BLECharacteristic::PROPERTY_WRITE
    );
    pRxCharacteristic->setCallbacks(new MyCallbacks());

    pService->start();
    BLEDevice::getAdvertising()->addServiceUUID(SERVICE_UUID);
    pServer->getAdvertising()->start();
    Serial.println(">> Nudge Ready (Sensors + Servos + Gesture Model)");
}

void updateGesture() {
    int rawResult = -1;
    float probability = 0;
    if (!GestureModel::Predict(rawResult, probability)) {
        confirmedClassification = -1;
        return;
    }
    if (probability < CONFIDENCE_REQUIRED) rawResult = -1;

    if (rawResult != lastRawClassification) {
        lastRawClassification = rawResult;
        classificationCounter = 1;
        return;
    }
    if (++classificationCounter < CLASSIFICATION_STABILITY_REQUIRED) return;
    confirmedClassification = rawResult;

    /**
     * GATED AI ACTUATION
     * Only move servos if explicitly enabled by "ai_start" command, and only when the
     * gesture changes. UNKNOWN (-1) holds the current position.
     */
    if (!ServoController::IsAIActuationEnabled()) {
        actuatedClassification = -1;
        return;
    }
    if (confirmedClassification == -1 || confirmedClassification == actuatedClassification) return;
    actuatedClassification = confirmedClassification;

    if (confirmedClassification == 0 || confirmedClassification == 2) {
        // Close or Pinch detected -> Move to Grasp
        ServoController::MoveGrasp(255, 150);
    } else {
        // Open or Rest detected -> Move to Retract
        ServoController::MoveRetract(255, 150);
    }
}

void loop() {
    if (deviceConnected) {
        // 1. Read Sensors
        float sensors[] = {(float)analogRead(D0), (float)analogRead(D1), (float)analogRead(D2)};

        // 2. Classify the last 200 ms
        GestureModel::Push(sensors);
        updateGesture();

        // 3. Send readings, gesture and model id to the app
        uint8_t packet[42];
        packet[0] = 0x01; packet[1] = 0x00; packet[2] = messageId++;
        packet[3] = 0x10; packet[4] = 0x00; packet[5] = 36;

        int offset = 6;
        for(int i=0; i<3; i++) {
            packet[offset++] = (TYPE_FLOAT >> 8) & 0xFF; packet[offset++] = TYPE_FLOAT & 0xFF;
            packet[offset++] = 0x00; packet[offset++] = 0x04;
            memcpy(&packet[offset], &sensors[i], 4); offset += 4;
        }
        packet[offset++] = (TYPE_INT16 >> 8) & 0xFF; packet[offset++] = TYPE_INT16 & 0xFF;
        packet[offset++] = 0x00; packet[offset++] = 0x02;
        int16_t aiResult = (int16_t)confirmedClassification;
        memcpy(&packet[offset], &aiResult, 2); offset += 2;

        packet[offset++] = (TYPE_UINT16 >> 8) & 0xFF; packet[offset++] = TYPE_UINT16 & 0xFF;
        packet[offset++] = 0x00; packet[offset++] = 0x02;
        uint16_t modelId = GestureModel::ModelId();
        memcpy(&packet[offset], &modelId, 2); offset += 2;

        pTxCharacteristic->setValue(packet, sizeof(packet));
        pTxCharacteristic->notify();

        delay(20);
    }
}

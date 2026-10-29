/**
 * Nudge Wearable Firmware - main board (Nudge.kicad_sch) with a XIAO ESP32-S3
 * 16-pad EMG strip: TI ADS1298, 8 differential channels over SPI (see WIRING.md)
 * On-device gesture model trained by the app (see GestureModel.h)
 * Two DC motors with encoders, force sensors and battery (see MotorController.h)
 * Pins: BoardConfig.h. Build for board XIAO_ESP32S3.
 * Using Custom Binary TLV Protocol
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include "BoardConfig.h"
#include "MotorController.h"
#include "GestureModel.h"
#include "ADS1298.h"

// UUIDs - MUST MATCH ANDROID APP
#define SERVICE_UUID        "000B1E53-D47A-CEDE-DE57-000000008488"
#define CHAR_TX_UUID        "00008488-D47A-CEDE-0000-466178454D47"
#define CHAR_RX_UUID        "00008288-D47A-CEDE-0000-526563436D64"

BLEServer* pServer = NULL;
BLECharacteristic* pTxCharacteristic = NULL;
bool deviceConnected = false;
uint8_t messageId = 0;
bool sensorReady = false;

const uint16_t TYPE_FLOAT = 0x1130;
const uint16_t TYPE_INT16 = 0x1112;
const uint16_t TYPE_UINT16 = 0x1116;
const uint16_t TYPE_UINT8 = 0x1115;

// --- AI SMOOTHING ---
const float CONFIDENCE_REQUIRED = 0.80f;
const int CLASSIFICATION_STABILITY_REQUIRED = 3;
int classificationCounter = 0;
int lastRawClassification = -1;
int confirmedClassification = -1;
int actuatedClassification = -1;

/** Commands arrive from the app over BLE, or typed into the Serial Monitor. */
void handleCommand(String value) {
    value.trim();
    if (value.length() == 0) return;
    if (GestureModel::HandleCommand(value)) return;
    if (ADS1298::HandleCommand(value)) return;
    Serial.print("Command Received: ");
    Serial.println(value);
    if (!MotorController::ParseCommand(value)) Serial.println("Unknown command");
}

class MyServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println(">> App Connected");
    };
    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        // Never keep moving the hand on AI output once the app is gone
        MotorController::EnableAIActuation(false);
        Serial.println(">> App Disconnected");
        BLEDevice::startAdvertising();
    }
};

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        handleCommand(pCharacteristic->getValue());
    }
};

void setup() {
    Serial.begin(115200);
    GestureModel::Init();

    // Safe start: opens the hand gently until each motor reaches its end stop
    MotorController::Init();

    sensorReady = ADS1298::Begin();
    if (!sensorReady) Serial.println(">> EMG strip not working; motors and BLE still run. See WIRING.md.");

    BLEDevice::init("Nudge");
    // Room for 8-channel packets and model upload lines
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
    Serial.println(">> Nudge Ready (EMG strip + Motors + Gesture Model). Try 'status' or 'ads diag on'.");
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
    if (!MotorController::IsAIActuationEnabled()) {
        actuatedClassification = -1;
        return;
    }
    if (confirmedClassification == -1 || confirmedClassification == actuatedClassification) return;
    actuatedClassification = confirmedClassification;

    if (confirmedClassification == 0 || confirmedClassification == 2) {
        // Close or Pinch detected -> Move to Grasp
        MotorController::MoveGrasp(255, 150);
    } else {
        // Open or Rest detected -> Move to Retract
        MotorController::MoveRetract(255, 150);
    }
}

void sendPacket(const float envelope[ADS1298::CHANNELS]) {
    // EMG floats, gesture, model id, then battery % and the two force sensors
    constexpr int SIZE = 6 + ADS1298::CHANNELS * 8 + 6 + 6 + 5 + 6 + 6;
    uint8_t packet[SIZE];
    packet[0] = 0x01; packet[1] = 0x00; packet[2] = messageId++;
    packet[3] = 0x10; packet[4] = 0x00; packet[5] = SIZE - 6;

    int offset = 6;
    for (int i = 0; i < ADS1298::CHANNELS; i++) {
        packet[offset++] = (TYPE_FLOAT >> 8) & 0xFF; packet[offset++] = TYPE_FLOAT & 0xFF;
        packet[offset++] = 0x00; packet[offset++] = 0x04;
        memcpy(&packet[offset], &envelope[i], 4); offset += 4;
    }
    packet[offset++] = (TYPE_INT16 >> 8) & 0xFF; packet[offset++] = TYPE_INT16 & 0xFF;
    packet[offset++] = 0x00; packet[offset++] = 0x02;
    int16_t aiResult = (int16_t)confirmedClassification;
    memcpy(&packet[offset], &aiResult, 2); offset += 2;

    packet[offset++] = (TYPE_UINT16 >> 8) & 0xFF; packet[offset++] = TYPE_UINT16 & 0xFF;
    packet[offset++] = 0x00; packet[offset++] = 0x02;
    uint16_t modelId = GestureModel::ModelId();
    memcpy(&packet[offset], &modelId, 2); offset += 2;

    packet[offset++] = (TYPE_UINT8 >> 8) & 0xFF; packet[offset++] = TYPE_UINT8 & 0xFF;
    packet[offset++] = 0x00; packet[offset++] = 0x01;
    packet[offset++] = MotorController::BatteryPercent();

    uint16_t forces[] = {MotorController::ForceThumb(), MotorController::ForceIndex()};
    for (uint16_t f : forces) {
        packet[offset++] = (TYPE_UINT16 >> 8) & 0xFF; packet[offset++] = TYPE_UINT16 & 0xFF;
        packet[offset++] = 0x00; packet[offset++] = 0x02;
        memcpy(&packet[offset], &f, 2); offset += 2;
    }

    pTxCharacteristic->setValue(packet, sizeof(packet));
    pTxCharacteristic->notify();
}

void loop() {
    // Serial commands, e.g. "ads mode shorted" during bring-up
    if (Serial.available()) handleCommand(Serial.readStringUntil('\n'));
    ADS1298::PrintDiagnostics();

    // One envelope frame every 20 ms: 8 channels of muscle activity in microvolts
    float envelope[ADS1298::CHANNELS];
    if (!sensorReady || !ADS1298::NextEnvelope(envelope, 50)) return;

    GestureModel::Push(envelope);
    updateGesture();
    if (deviceConnected) sendPacket(envelope);
}

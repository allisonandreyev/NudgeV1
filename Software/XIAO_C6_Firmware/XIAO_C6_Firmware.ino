/**
 * Nudge Wearable Firmware - XIAO ESP32-C6
 * AI Inference Integrated (D1, D2 Sensors)
 * Triple EMG Sensor Stream (D0, D1, D2)
 * Remote Servo Control (0-5)
 * Using Custom Binary TLV Protocol
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include "ServoController.h"

// Edge Impulse library header for nudgeml_v2
#include <NudgeML_V2_inferencing.h>

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

// --- AI BUFFERING & SMOOTHING ---
float features[EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE];
int featureIndex = 0;

int classificationCounter = 0;
const int CLASSIFICATION_STABILITY_REQUIRED = 3;
int lastRawClassification = -1;
int confirmedClassification = -1;

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

class MyCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
        String value = pCharacteristic->getValue();
        if (value.length() > 0) {
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
    Serial.println(">> Nudge Ready (Sensors + Servos + TinyML)");
}

void loop() {
    if (deviceConnected) {
        // 1. Read Sensors
        float emg0 = analogRead(D0);
        float emg1 = analogRead(D1);
        float emg2 = analogRead(D2);

        features[featureIndex++] = emg1;
        features[featureIndex++] = emg2;

        if (featureIndex >= EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE) {
            signal_t signal;
            numpy::signal_from_buffer(features, EI_CLASSIFIER_DSP_INPUT_FRAME_SIZE, &signal);

            ei_impulse_result_t result = { 0 };
            EI_IMPULSE_ERROR res = run_classifier(&signal, &result, false);

            if (res == EI_IMPULSE_OK) {
                float max_val = 0;
                int rawResult = -1;
                for (size_t ix = 0; ix < EI_CLASSIFIER_LABEL_COUNT; ix++) {
                    if (result.classification[ix].value > max_val) {
                        max_val = result.classification[ix].value;
                        rawResult = (int)ix;
                    }
                }

                if (max_val < 0.80f) rawResult = -1;

                if (rawResult == lastRawClassification) {
                    classificationCounter++;
                    if (classificationCounter >= CLASSIFICATION_STABILITY_REQUIRED) {
                        confirmedClassification = rawResult;

                        /**
                         * GATED AI ACTUATION
                         * Only move servos if explicitly enabled by "ai_start" command
                         */
                        if (ServoController::IsAIActuationEnabled()) {
                            if (confirmedClassification == 0 || confirmedClassification == 2) {
                                ServoController::MoveGrasp(255, 150);
                            } else {
                                ServoController::MoveRetract(255, 150);
                            }
                        }
                    }
                } else {
                    lastRawClassification = rawResult;
                    classificationCounter = 1;
                }
            }
            featureIndex = 0;
        }

        uint8_t packet[36];
        packet[0] = 0x01; packet[1] = 0x00; packet[2] = messageId++;
        packet[3] = 0x10; packet[4] = 0x00; packet[5] = 30;

        int offset = 6;
        float sensors[] = {emg0, emg1, emg2};
        for(int i=0; i<3; i++) {
            packet[offset++] = (TYPE_FLOAT >> 8) & 0xFF; packet[offset++] = TYPE_FLOAT & 0xFF;
            packet[offset++] = 0x00; packet[offset++] = 0x04;
            memcpy(&packet[offset], &sensors[i], 4); offset += 4;
        }
        packet[offset++] = (TYPE_INT16 >> 8) & 0xFF; packet[offset++] = TYPE_INT16 & 0xFF;
        packet[offset++] = 0x00; packet[offset++] = 0x02;
        int16_t aiResult = (int16_t)confirmedClassification;
        memcpy(&packet[offset], &aiResult, 2); offset += 2;

        pTxCharacteristic->setValue(packet, 36);
        pTxCharacteristic->notify();

        delay(20);
    }
}

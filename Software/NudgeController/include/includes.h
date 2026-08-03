#pragma once

// General includes
#include <Arduino.h>
#include <Wire.h>
#include <unordered_map>
#include <string>
#include <functional>
#include <vector>
#include <type_traits>
#include <array>

// BLE includes
#include "BLE.h"
#include "BLECallbacks.h"
#include "Packet.h"
#include <NimBLEDevice.h>

// Servo includes
#include "ServoController.h"
#include <Adafruit_PWMServoDriver.h>
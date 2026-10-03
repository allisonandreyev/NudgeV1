// Minimal stand-ins so GestureModel.cpp compiles on a computer for testing
#pragma once
#include <string>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <cstdint>
#include <cmath>
struct SerialShim { template <typename... A> void printf(const char* f, A... a) { std::printf(f, a...); } void println(const char* s) { std::puts(s); } };
inline SerialShim Serial;
class String {
  std::string s;
public:
  String(const char* c) : s(c) {}
  String(const std::string& c) : s(c) {}
  bool startsWith(const char* p) const { return s.rfind(p, 0) == 0; }
  const char* c_str() const { return s.c_str(); }
};
typedef void* SemaphoreHandle_t;
#define portMAX_DELAY 0
inline SemaphoreHandle_t xSemaphoreCreateMutex() { return (void*)1; }
inline void xSemaphoreTake(SemaphoreHandle_t, int) {}
inline void xSemaphoreGive(SemaphoreHandle_t) {}

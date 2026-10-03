#pragma once
#include <cstddef>
#include <cstring>
#include <vector>
// In-memory flash, so a test can check that a saved model is loaded again
class Preferences {
  static std::vector<unsigned char>& store() { static std::vector<unsigned char> s; return s; }
public:
  void begin(const char*, bool) {}
  void end() {}
  size_t getBytesLength(const char*) { return store().size(); }
  size_t getBytes(const char*, void* buf, size_t len) { std::memcpy(buf, store().data(), len); return len; }
  size_t putBytes(const char*, const void* buf, size_t len) { store().assign((const unsigned char*)buf, (const unsigned char*)buf + len); return len; }
};

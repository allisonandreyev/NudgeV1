#include "./Packet.h"
#include <vector>
#include "Packet.h"
#include <type_traits>

Packet::Packet()
{

}

Packet::~Packet()
{

}

void Packet::SetVersion(uint8_t v) { version = v; }
void Packet::SetFlags(uint8_t f) { flags = f; }
void Packet::SetMessageID(uint8_t id) { messageID = id; }

void Packet::AppendType(TypeCode type)
{
  uint16_t value = static_cast<uint16_t>(type);

  payloadData.push_back(static_cast<uint8_t>(value >> 8));
  payloadData.push_back(static_cast<uint8_t>(value));
}

void Packet::ClearData()
{
  payloadData.clear();
}

// std::vector<uint8_t> Packet::Serialize() const
// {
//   std::vector<uint8_t> data = {1};
//   return data;
// }

// bool Packet::Deserialize(const uint8_t*, size_t)
// {
//   return false;
// }

// segmentData = (totalSegments << 4) | currentSegment;
// void Packet::AppendByte(const uint8_t *, size_t)
// {
  
// }
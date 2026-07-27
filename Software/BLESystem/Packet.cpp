#include "./Packet.h"
#include <vector>
#include "Packet.h"
#include <type_traits>
#include <unordered_map>

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

  payload.push_back(static_cast<uint8_t>(value >> 8));
  payload.push_back(static_cast<uint8_t>(value));
}

void Packet::ClearData()
{
  payload.clear();
}

std::vector<uint8_t> Packet::Serialize()
{
  PrependHeader();
  return payload;
}

void Packet::PrependHeader()
{
  // insert header in reverse order to get the correct structure
  // segment data will come later

  // Payload Length
  uint16_t len = static_cast<uint16_t>(payload.size());
  payload.insert(payload.begin(), static_cast<uint8_t>(len & 0xFF));
  payload.insert(payload.begin(), static_cast<uint8_t>((len >> 8) & 0xFF));

  // Segment data (just zeros for now)
  payload.insert(payload.begin(), 0x00);

  // Message ID
  payload.insert(payload.begin(), messageID);

  // Flags
  payload.insert(payload.begin(), flags);

  // Version
  payload.insert(payload.begin(), version);
}

// bool Packet::Deserialize(const uint8_t*, size_t)
// {
//   return false;
// }

// segmentData = (totalSegments << 4) | currentSegment;
// void Packet::AppendByte(const uint8_t *, size_t)
// {
  
// }
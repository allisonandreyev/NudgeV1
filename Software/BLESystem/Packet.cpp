#include "./Packet.h"
#include "./BLE.h"
#include <vector>
#include <type_traits>
#include <unordered_map>

Packet::Packet()
{
  
}

Packet::~Packet()
{
  payload.clear();
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

std::vector<std::vector<uint8_t>> Packet::Serialize()
{
  std::vector<std::vector<uint8_t>> packets;

  MTU = BLE::GetMTU();

  if(MTU <= 19)
  {
    Serial.println("MTU too small");
    return {};
  }

  size_t maxPayload = MTU - 3 - 6 - 10; // Max Size - BLE overhead (3) - Packet Header (6)
  size_t totalSegments = (payload.size() + maxPayload - 1) / maxPayload;

  for(size_t i = 0; i < totalSegments; i++)
  {
    size_t start = i * maxPayload;
    size_t end = std::min(start + maxPayload, payload.size());

    std::vector<uint8_t> packet;

    packet.push_back(version); // Version
    packet.push_back(flags); // Flags
    packet.push_back(messageID); // Message ID
    
    // Segment data
    uint8_t segment = ((totalSegments & 0x0F) << 4) | (i & 0x0F); 
    packet.push_back(segment);

    // Payload Length
    uint16_t len = payload.size();
    packet.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
    packet.push_back(static_cast<uint8_t>(len & 0xFF));

    // Add the segment to the payload
    packet.insert(packet.end(), payload.begin() + start, payload.begin() + end);

    // Add the packet to the queue
    packets.push_back(packet);
  }

  Serial.printf("Split packet into %u packets.\r\n", totalSegments);
  return packets;
}

// bool Packet::Deserialize(const uint8_t*, size_t)
// {
//   return false;
// }

// segmentData = (totalSegments << 4) | currentSegment;
// void Packet::AppendByte(const uint8_t *, size_t)
// {
  
// }
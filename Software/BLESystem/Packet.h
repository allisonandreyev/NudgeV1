#pragma once
#include <Arduino.h>
#include <string>
#include <unordered_map>
#include <vector>

/*
PACKET STRUCTURE
Byte 0: Version
Byte 1: Flags
Byte 2: Message ID
Byte 3: Segment Data
  Bit 7-4: Total
  Bit 3-0: Current
Byte 4-5: Total Length (uint16_t)
Byte 6+: Payload - TLV structure
*/

enum class TypeCode : uint16_t
{
  Int8     = 0x1111,
  Int16    = 0x1112,
  Int32    = 0x1113,
  Int64    = 0x1114,

  UInt8    = 0x1115,
  UInt16   = 0x1116,
  UInt32   = 0x1117,
  UInt64   = 0x1118,

  Bool     = 0x1120,
  Float    = 0x1130,
  Double   = 0x1140,

  String   = 0x1150,
  Raw      = 0x11FF,
};

class Packet
{
  public:
    Packet();
    ~Packet();
    void SetVersion(uint8_t v);
    void SetFlags(uint8_t f);
    void SetMessageID(uint8_t id);
    // void SetSegments(uint8_t segments);

    template<typename T> /* Catch-all for unsupported data types. Sends data as raw binary bits */
    void Append(const T& value)
    {
      // type
      AppendType(GetType<T>());

      // length
      uint16_t len = sizeof(T);
      payload.push_back(static_cast<uint8_t>(len >> 8));
      payload.push_back(static_cast<uint8_t>(len));

      // data
      const auto* ptr = reinterpret_cast<const uint8_t*>(&value);
      payload.insert(payload.end(), ptr, ptr + len);
    }

    void ClearData();
    // void AppendByte(const uint8_t*, size_t);

    std::vector<uint8_t> Serialize();
    // bool Deserialize(const uint8_t*, size_t);

  private:
    uint8_t version = 1;
    uint8_t flags = 0;
    uint8_t messageID = 0;

    uint8_t totalSegments = 0;
    uint8_t currentSegment = 0;
    std::vector<uint8_t> payload; // split into 8 bit chunks to grow/shrink as needed
    
    void AppendType(TypeCode type);
    void PrependHeader();
    template<typename T>
    TypeCode GetType()
    {
      if constexpr        (std::is_same_v<T, int8_t>)       return TypeCode::Int8;
      else if constexpr   (std::is_same_v<T, int16_t>)      return TypeCode::Int16;
      else if constexpr   (std::is_same_v<T, int32_t>)      return TypeCode::Int32;
      else if constexpr   (std::is_same_v<T, int64_t>)      return TypeCode::Int64;
      else if constexpr   (std::is_same_v<T, uint8_t>)      return TypeCode::UInt8;
      else if constexpr   (std::is_same_v<T, uint16_t>)     return TypeCode::UInt16;
      else if constexpr   (std::is_same_v<T, uint32_t>)     return TypeCode::UInt32;
      else if constexpr   (std::is_same_v<T, uint64_t>)     return TypeCode::UInt64;
      else if constexpr   (std::is_same_v<T, bool>)         return TypeCode::Bool;
      else if constexpr   (std::is_same_v<T, float>)        return TypeCode::Float;
      else if constexpr   (std::is_same_v<T, double>)       return TypeCode::Double;
      else if constexpr   (std::is_same_v<T, std::string>)  return TypeCode::String;
      else return TypeCode::Raw;
    }
};
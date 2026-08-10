package com.nudge.app.bluetooth

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class TypeCode(val value: Int) {
    Int8(0x1111),
    Int16(0x1112),
    Int32(0x1113),
    Int64(0x1114),
    UInt8(0x1115),
    UInt16(0x1116),
    UInt32(0x1117),
    UInt64(0x1118),
    Bool(0x1120),
    Float(0x1130),
    Double(0x1140),
    String(0x1150),
    Raw(0x11FF);

    companion object {
        fun fromInt(value: Int): TypeCode = entries.find { it.value == value } ?: Raw
    }
}

data class Packet(
    val version: Int,
    val flags: Int,
    val messageId: Int,
    val data: List<Any>
)

class PacketDeserializer {
    private val segmentBuffers = mutableMapOf<Int, MutableMap<Int, ByteArray>>()
    private val totalSegmentsMap = mutableMapOf<Int, Int>()

    fun processSegment(segment: ByteArray): Packet? {
        if (segment.size < 6) {
            if (segment.isNotEmpty()) Log.w("Packet", "Segment too small: ${segment.size}")
            return null
        }

        try {
            val version = segment[0].toInt() and 0xFF
            val flags = segment[1].toInt() and 0xFF
            val messageId = segment[2].toInt() and 0xFF
            
            val segmentInfo = segment[3].toInt() and 0xFF
            val totalSegments = (segmentInfo shr 4) and 0x0F
            val currentSegment = segmentInfo and 0x0F

            // Length of the TOTAL payload across all segments
            val totalPayloadLength = ((segment[4].toInt() and 0xFF) shl 8) or (segment[5].toInt() and 0xFF)

            val payloadPart = segment.copyOfRange(6, segment.size)
            // Log.d("Packet", "Segment: msgId=$messageId, index=$currentSegment, totalSegs=$totalSegments, partSize=${payloadPart.size}, totalExp=$totalPayloadLength")

            if (totalSegments <= 1) {
                return decodePayload(version, flags, messageId, payloadPart)
            }

            // Handle multi-segment reassembly
            // If we receive a new messageId, consider cleaning up old incomplete messages
            if (!segmentBuffers.containsKey(messageId) && segmentBuffers.size > 5) {
                segmentBuffers.clear() 
                totalSegmentsMap.clear()
            }

            val buffers = segmentBuffers.getOrPut(messageId) { mutableMapOf() }
            buffers[currentSegment] = payloadPart
            
            Log.d("Packet", "Assembly progress for msgId $messageId: ${buffers.size}/$totalSegments")

            if (buffers.size == totalSegments) {
                Log.i("Packet", "All segments received for msgId $messageId. Reassembling...")
                val fullPayload = ByteBuffer.allocate(totalPayloadLength)
                // Using 0-based indexing (0..totalSegments-1)
                for (i in 0 until totalSegments) {
                    val b = buffers[i]
                    if (b != null) {
                        fullPayload.put(b)
                    } else {
                        Log.e("Packet", "Reassembly failed: Missing segment $i for message $messageId")
                        segmentBuffers.remove(messageId)
                        return null
                    }
                }
                
                segmentBuffers.remove(messageId)
                totalSegmentsMap.remove(messageId)
                
                return decodePayload(version, flags, messageId, fullPayload.array())
            }
        } catch (e: Exception) {
            Log.e("Packet", "Error processing segment", e)
        }

        return null
    }

    private fun decodePayload(version: Int, flags: Int, messageId: Int, payload: ByteArray): Packet {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        val decodedData = mutableListOf<Any>()

        // val hexPayload = payload.joinToString("-") { "%02X".format(it) }
        // Log.d("Packet", "Decoding payload of size ${payload.size}: $hexPayload")

        while (buffer.remaining() >= 4) {
            val typeVal = buffer.short.toInt() and 0xFFFF
            val length = buffer.short.toInt() and 0xFFFF
            val type = TypeCode.fromInt(typeVal)

            if (buffer.remaining() < length) {
                Log.w("Packet", "Buffer underflow for type $type: need $length, have ${buffer.remaining()}")
                break
            }

            val valueBytes = ByteArray(length)
            buffer.get(valueBytes)
            
            val valueBuffer = ByteBuffer.wrap(valueBytes).order(ByteOrder.LITTLE_ENDIAN)

            try {
                val decodedValue: Any = when (type) {
                    TypeCode.Int8 -> valueBuffer.get()
                    TypeCode.Int16 -> valueBuffer.short
                    TypeCode.Int32 -> valueBuffer.int
                    TypeCode.Int64 -> valueBuffer.long
                    TypeCode.UInt8 -> valueBuffer.get().toUByte()
                    TypeCode.UInt16 -> valueBuffer.short.toUShort()
                    TypeCode.UInt32 -> valueBuffer.int.toUInt()
                    TypeCode.UInt64 -> valueBuffer.long.toULong()
                    TypeCode.Bool -> valueBuffer.get() != 0.toByte()
                    TypeCode.Float -> valueBuffer.float
                    TypeCode.Double -> valueBuffer.double
                    TypeCode.String -> String(valueBytes)
                    TypeCode.Raw -> valueBytes
                }
                decodedData.add(decodedValue)
                // Log.d("Packet", "Decoded $type: $decodedValue")
            } catch (e: Exception) {
                Log.e("Packet", "Failed to decode type $type", e)
                decodedData.add(valueBytes) // Fallback to raw bytes
            }
        }

        return Packet(version, flags, messageId, decodedData)
    }
}

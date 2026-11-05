package org.openhue.streamtv.hue.stream

import java.io.ByteArrayOutputStream

/**
 * Representation of an RGB color for a Hue Entertainment light.
 * R, G, B channels are represented as 0..255 (or 0..65535 when expanded to 16-bit).
 */
data class LightRgbColor(
    val lightId: Int,
    val red: Int,   // 0..255
    val green: Int, // 0..255
    val blue: Int   // 0..255
)

/**
 * Serializer for Philips Hue Entertainment streaming protocol v1.
 * 
 * Header layout (16 bytes):
 * - [0..8]   "HueStream" (ASCII, 9 bytes)
 * - [9..10]  Version: 0x01, 0x00 (Major 1, Minor 0)
 * - [11]     Sequence number: 0x00..0xFF
 * - [12..13] Reserved: 0x00, 0x00
 * - [14]     Color space: 0x00 (RGB)
 * - [15]     Reserved: 0x00
 *
 * Light payload layout (9 bytes per light):
 * - [0]      Type: 0x00 (Light)
 * - [1..2]   Light ID: 16-bit unsigned integer (big-endian)
 * - [3..4]   Red: 16-bit integer (big-endian, 0..65535)
 * - [5..6]   Green: 16-bit integer (big-endian, 0..65535)
 * - [7..8]   Blue: 16-bit integer (big-endian, 0..65535)
 */
object HuePacketBuilder {
    private val PROTOCOL_HEADER = "HueStream".toByteArray(Charsets.US_ASCII)
    private var sequenceNumber: Byte = 0

    @Synchronized
    fun buildPacket(lights: List<LightRgbColor>): ByteArray {
        val stream = ByteArrayOutputStream(16 + lights.size * 9)

        // 1. Protocol ID (9 bytes)
        stream.write(PROTOCOL_HEADER)

        // 2. Protocol Version 1.0 (2 bytes)
        stream.write(0x01)
        stream.write(0x00)

        // 3. Sequence Number (1 byte)
        stream.write(sequenceNumber.toInt() and 0xFF)
        sequenceNumber = ((sequenceNumber.toInt() + 1) and 0xFF).toByte()

        // 4. Reserved (2 bytes)
        stream.write(0x00)
        stream.write(0x00)

        // 5. Color Space: 0x00 (RGB)
        stream.write(0x00)

        // 6. Reserved (1 byte)
        stream.write(0x00)

        // 7. Per-light payload (9 bytes each)
        for (light in lights) {
            // Type: 0x00 = Light
            stream.write(0x00)

            // Light ID (2 bytes, big-endian)
            val id = light.lightId and 0xFFFF
            stream.write((id shr 8) and 0xFF)
            stream.write(id and 0xFF)

            // Expand 8-bit RGB to 16-bit: (channel * 257) maps 0..255 linearly to 0..65535
            val r16 = (light.red.coerceIn(0, 255) * 257) and 0xFFFF
            val g16 = (light.green.coerceIn(0, 255) * 257) and 0xFFFF
            val b16 = (light.blue.coerceIn(0, 255) * 257) and 0xFFFF

            // Red (2 bytes, big-endian)
            stream.write((r16 shr 8) and 0xFF)
            stream.write(r16 and 0xFF)

            // Green (2 bytes, big-endian)
            stream.write((g16 shr 8) and 0xFF)
            stream.write(g16 and 0xFF)

            // Blue (2 bytes, big-endian)
            stream.write((b16 shr 8) and 0xFF)
            stream.write(b16 and 0xFF)
        }

        return stream.toByteArray()
    }

    fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.trim()
        val len = cleanHex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(cleanHex[i], 16) shl 4) +
                    Character.digit(cleanHex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}

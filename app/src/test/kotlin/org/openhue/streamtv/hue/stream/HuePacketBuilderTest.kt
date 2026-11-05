package org.openhue.streamtv.hue.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openhue.streamtv.capture.ColorExtractor
import org.openhue.streamtv.hue.model.LightLocation

class HuePacketBuilderTest {

    @Test
    fun testBuildPacketHeader() {
        val lights = listOf(
            LightRgbColor(lightId = 1, red = 255, green = 0, blue = 128),
            LightRgbColor(lightId = 2, red = 10, green = 20, blue = 30)
        )

        val packet = HuePacketBuilder.buildPacket(lights)

        // 16 bytes header + 2 * 9 bytes lights = 34 bytes
        assertEquals(34, packet.size)

        // Protocol name: "HueStream"
        val headerName = String(packet.copyOfRange(0, 9), Charsets.US_ASCII)
        assertEquals("HueStream", headerName)

        // Version: 0x01, 0x00
        assertEquals(0x01.toByte(), packet[9])
        assertEquals(0x00.toByte(), packet[10])

        // Color space: 0x00 (RGB) at index 14
        assertEquals(0x00.toByte(), packet[14])

        // First light: type = 0x00 at index 16
        assertEquals(0x00.toByte(), packet[16])
        // Light ID: 1 (0x00, 0x01)
        assertEquals(0x00.toByte(), packet[17])
        assertEquals(0x01.toByte(), packet[18])

        // Red: 255 * 257 = 65535 = 0xFFFF
        assertEquals(0xFF.toByte(), packet[19])
        assertEquals(0xFF.toByte(), packet[20])
    }

    @Test
    fun testHexToBytes() {
        val hex = "0123456789abcdef0123456789abcdef"
        val bytes = HuePacketBuilder.hexToBytes(hex)
        assertEquals(16, bytes.size)
        assertEquals(0x01.toByte(), bytes[0])
        assertEquals(0xef.toByte(), bytes[7])
    }

    @Test
    fun testMapLocationsToTargets() {
        val locations = mapOf(
            "1" to LightLocation(x = -1.0, y = 1.0, z = 0.0), // Far left
            "2" to LightLocation(x = 1.0, y = 1.0, z = 0.0),  // Far right
            "3" to LightLocation(x = 0.0, y = 1.0, z = 1.0)   // Top center
        )

        val targets = ColorExtractor.mapLocationsToTargets(locations)
        assertEquals(3, targets.size)

        val t1 = targets.first { it.lightId == 1 }
        assertEquals(0.15f, t1.normX, 0.01f) // clamped near left

        val t2 = targets.first { it.lightId == 2 }
        assertEquals(0.85f, t2.normX, 0.01f) // clamped near right

        val t3 = targets.first { it.lightId == 3 }
        assertEquals(0.5f, t3.normX, 0.01f) // center X
        assertEquals(0.25f, t3.normY, 0.01f) // top quarter Y
    }
}

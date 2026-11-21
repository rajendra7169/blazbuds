package com.blazify.buds.desktop

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** MQTT 3.1.1 packets, byte by byte, as the specification lays them out. */
class MqttTest {
    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ascii(s: String) = s.map { it.code }.toIntArray()

    @Test
    fun remainingLengthAtEveryBoundary() {
        val cases = mapOf(
            0 to bytes(0x00),
            127 to bytes(0x7F),
            128 to bytes(0x80, 0x01),
            321 to bytes(0xC1, 0x02),
            16_383 to bytes(0xFF, 0x7F),
            16_384 to bytes(0x80, 0x80, 0x01),
            2_097_151 to bytes(0xFF, 0xFF, 0x7F),
            2_097_152 to bytes(0x80, 0x80, 0x80, 0x01),
            268_435_455 to bytes(0xFF, 0xFF, 0xFF, 0x7F),
        )
        cases.forEach { (n, encoded) ->
            assertContentEquals(encoded, Mqtt.length(n), "length $n")
            assertEquals(n, Mqtt.readLength(ByteArrayInputStream(encoded)), "read $n")
        }
        assertFailsWith<IllegalArgumentException> { Mqtt.length(268_435_456) }
        assertFailsWith<java.io.IOException> { Mqtt.readLength(ByteArrayInputStream(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0x01))) }
    }

    @Test
    fun connectWithWillAndLogin() {
        val packet = Mqtt.connect("bb", "u", "pw", 30, Mqtt.Will("a/b", "off".encodeToByteArray(), retain = true))
        val body = intArrayOf(
            0x00, 0x04, *ascii("MQTT"), 0x04,
            // username, password, will retain, will flag, clean session
            0b1110_0110,
            0x00, 30,
            0x00, 0x02, *ascii("bb"),
            0x00, 0x03, *ascii("a/b"),
            0x00, 0x03, *ascii("off"),
            0x00, 0x01, *ascii("u"),
            0x00, 0x02, *ascii("pw"),
        )
        assertContentEquals(bytes(0x10, body.size, *body), packet)
    }

    @Test
    fun connectWithoutLogin() {
        val packet = Mqtt.connect("bb", null, null, 60, null)
        val body = intArrayOf(0x00, 0x04, *ascii("MQTT"), 0x04, 0x02, 0x00, 60, 0x00, 0x02, *ascii("bb"))
        assertContentEquals(bytes(0x10, body.size, *body), packet)
    }

    @Test
    fun publishRetainedAtQos0() {
        assertContentEquals(bytes(0x31, 7, 0x00, 0x03, *ascii("a/b"), *ascii("hi")), Mqtt.publish("a/b", "hi".encodeToByteArray(), retain = true))
        assertContentEquals(bytes(0x30, 5, 0x00, 0x03, *ascii("a/b")), Mqtt.publish("a/b", ByteArray(0), retain = false))
    }

    @Test
    fun longPublishUsesTwoLengthBytes() {
        val packet = Mqtt.publish("t", ByteArray(200), retain = false)
        // 2 + 1 topic bytes + 200 = 203 = 0xCB 0x01
        assertContentEquals(bytes(0x30, 0xCB, 0x01, 0x00, 0x01, 't'.code), packet.copyOfRange(0, 6))
        assertEquals(3 + 203, packet.size)
    }

    @Test
    fun subscribeAsksQos0() {
        val packet = Mqtt.subscribe(1, listOf("a/b", "c"))
        val body = intArrayOf(0x00, 0x01, 0x00, 0x03, *ascii("a/b"), 0x00, 0x00, 0x01, *ascii("c"), 0x00)
        assertContentEquals(bytes(0x82, body.size, *body), packet)
    }

    @Test
    fun smallPackets() {
        assertContentEquals(bytes(0xC0, 0x00), Mqtt.pingReq)
        assertContentEquals(bytes(0xE0, 0x00), Mqtt.disconnect)
        assertContentEquals(bytes(0x40, 0x02, 0x01, 0x02), Mqtt.pubAck(0x0102))
    }

    @Test
    fun readsWhatTheBrokerSends() {
        assertEquals(Mqtt.Packet.ConnAck(false, 0), Mqtt.read(ByteArrayInputStream(bytes(0x20, 0x02, 0x00, 0x00))))
        assertEquals(Mqtt.Packet.ConnAck(true, 5), Mqtt.read(ByteArrayInputStream(bytes(0x20, 0x02, 0x01, 0x05))))
        assertEquals(Mqtt.Packet.SubAck(7, listOf(0, 0x80)), Mqtt.read(ByteArrayInputStream(bytes(0x90, 0x04, 0x00, 0x07, 0x00, 0x80))))
        assertEquals(Mqtt.Packet.PingResp, Mqtt.read(ByteArrayInputStream(bytes(0xD0, 0x00))))
        val qos0 = assertIs<Mqtt.Packet.Publish>(Mqtt.read(ByteArrayInputStream(Mqtt.publish("x/y", "Normal".encodeToByteArray(), retain = true))))
        assertEquals("x/y", qos0.topic)
        assertEquals("Normal", qos0.text)
        assertEquals(true, qos0.retain)
        assertEquals(null, qos0.id)
        val qos1 = assertIs<Mqtt.Packet.Publish>(Mqtt.read(ByteArrayInputStream(bytes(0x32, 0x08, 0x00, 0x01, 'z'.code, 0x00, 0x09, *ascii("abc")))))
        assertEquals(1, qos1.qos)
        assertEquals(9, qos1.id)
        assertEquals("abc", qos1.text)
        assertFailsWith<java.io.EOFException> { Mqtt.read(ByteArrayInputStream(bytes(0x30, 0x05, 0x00))) }
    }
}

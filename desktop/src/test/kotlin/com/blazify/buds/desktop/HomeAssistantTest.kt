package com.blazify.buds.desktop

import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.DataInputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaDiscoveryTest {
    private val id = HaDiscovery.id("AA:BB:CC:DD:EE:FF")

    @Suppress("UNCHECKED_CAST")
    private fun json(text: String) = Json.read(text) as Map<String, Any?>

    @Test
    fun oneDeviceWithItsEntities() {
        assertEquals("aabbccddeeff", id)
        val configs = HaDiscovery.configs(id, DemoBuds.sample())
        assertEquals(
            listOf(
                "homeassistant/sensor/blazbuds_aabbccddeeff/left/config",
                "homeassistant/sensor/blazbuds_aabbccddeeff/right/config",
                "homeassistant/sensor/blazbuds_aabbccddeeff/case/config",
                "homeassistant/binary_sensor/blazbuds_aabbccddeeff/connected/config",
                "homeassistant/select/blazbuds_aabbccddeeff/listening_mode/config",
                "homeassistant/select/blazbuds_aabbccddeeff/scene/config",
            ),
            configs.keys.toList(),
        )
        val left = json(configs.getValue("homeassistant/sensor/blazbuds_aabbccddeeff/left/config"))
        assertEquals("Left battery", left["name"])
        assertEquals("blazbuds_aabbccddeeff_left", left["unique_id"])
        assertEquals("battery", left["device_class"])
        assertEquals("%", left["unit_of_measurement"])
        assertEquals("blazbuds/aabbccddeeff/state", left["state_topic"])
        assertEquals("blazbuds/aabbccddeeff/availability", left["availability_topic"])
        assertEquals("{{ value_json.left }}", left["value_template"])
        val device = left["device"] as Map<*, *>
        assertEquals("Blazbuds – Liberty 4 NC", device["name"])
        assertEquals(listOf("blazbuds_aabbccddeeff"), device["identifiers"])
        assertEquals("A3947", device["model"])

        val mode = json(configs.getValue("homeassistant/select/blazbuds_aabbccddeeff/listening_mode/config"))
        assertEquals(listOf("Noise cancelling", "Transparency", "Normal"), mode["options"])
        assertEquals("blazbuds/aabbccddeeff/mode/set", mode["command_topic"])
        val scene = json(configs.getValue("homeassistant/select/blazbuds_aabbccddeeff/scene/config"))
        assertEquals(listOf("Focus", "Commute", "Flight", "Talk", "Relax"), scene["options"])
        val connected = json(configs.getValue("homeassistant/binary_sensor/blazbuds_aabbccddeeff/connected/config"))
        assertEquals("connectivity", connected["device_class"])
    }

    @Test
    fun earbudsWithoutModesGetNoSelects() {
        val basic = DemoBuds.sample().copy(sound = null, left = null, right = null, case = null, overall = Battery(70))
        val configs = HaDiscovery.configs(id, basic)
        assertEquals(listOf("battery", "connected"), configs.keys.map { it.split("/")[3] })
        assertEquals(70L, json(HaDiscovery.statePayload(basic))["battery"])
    }

    @Test
    fun statePayload() {
        val state = json(HaDiscovery.statePayload(DemoBuds.sample()))
        assertEquals(true, state["connected"])
        assertEquals(80L, state["left"])
        assertEquals(60L, state["right"])
        assertEquals(100L, state["case"])
        assertEquals(true, state["case_charging"])
        assertEquals("Noise cancelling", state["mode"])
        assertEquals("Focus", state["scene"])
        val away = json(HaDiscovery.statePayload(DemoBuds.sample().copy(link = Link.Lost, left = null, right = null, case = null)))
        assertEquals(false, away["connected"])
        assertNull(away["left"])
    }

    @Test
    fun commandsFromHomeAssistant() {
        val s = DemoBuds.sample()
        assertEquals(listOf(Change.Mode(ListeningMode.Transparency)), HaDiscovery.command(id, HaDiscovery.modeCommand(id), "Transparency", s))
        assertEquals(listOf(Change.Mode(ListeningMode.Normal)), HaDiscovery.command(id, HaDiscovery.modeCommand(id), "normal", s))
        assertNull(HaDiscovery.command(id, HaDiscovery.modeCommand(id), "Loud", s))
        assertNotNull(HaDiscovery.command(id, HaDiscovery.sceneCommand(id), "Flight", s))
        assertNull(HaDiscovery.command(id, "blazbuds/other/mode/set", "Normal", s))
    }
}

/**
 * The whole bridge against a broker made of a server socket: what it connects with, what it publishes, a mode set
 * from Home Assistant reaching the earbuds, and a reconnect after the broker drops it.
 */
class HomeAssistantBridgeTest {
    private val node: Preferences = Preferences.userRoot().node("blazbuds-test/ha-" + System.nanoTime())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val broker = ServerSocket(0, 4, InetAddress.getLoopbackAddress())

    @AfterTest
    fun clean() {
        scope.cancel()
        broker.close()
        node.removeNode()
    }

    /** One packet as the broker sees it: its type, flags and body. */
    private class Raw(val type: Int, val flags: Int, val body: ByteArray) {
        val data = DataInputStream(body.inputStream())
        fun text(): String = ByteArray(data.readUnsignedShort()).also { data.readFully(it) }.decodeToString()
    }

    private fun read(input: InputStream): Raw {
        val first = input.read()
        check(first >= 0) { "closed" }
        val body = input.readNBytes(Mqtt.readLength(input))
        return Raw(first ushr 4, first and 0x0F, body)
    }

    private class Message(val topic: String, val payload: String, val retain: Boolean)

    /** Reads the client's packets on a thread, answering connect, subscribe and ping as a broker would. */
    private fun serve(peer: Socket, connects: LinkedBlockingQueue<Raw>, subscribed: LinkedBlockingQueue<List<String>>, published: LinkedBlockingQueue<Message>) {
        Thread {
            runCatching {
                val input = peer.getInputStream()
                val output = peer.getOutputStream()
                while (true) {
                    val raw = read(input)
                    when (raw.type) {
                        Mqtt.CONNECT -> { connects.put(raw); output.write(byteArrayOf(0x20, 0x02, 0x00, 0x00)) }
                        Mqtt.SUBSCRIBE -> {
                            val id = raw.data.readUnsignedShort()
                            val topics = mutableListOf<String>()
                            while (raw.data.available() > 0) { topics += raw.text(); raw.data.readByte() }
                            subscribed.put(topics)
                            output.write(byteArrayOf(0x90.toByte(), (2 + topics.size).toByte(), (id shr 8).toByte(), id.toByte()) + ByteArray(topics.size))
                        }
                        Mqtt.PUBLISH -> {
                            val topic = raw.text()
                            published.put(Message(topic, raw.data.readBytes().decodeToString(), raw.flags and 1 == 1))
                        }
                        Mqtt.PINGREQ -> output.write(byteArrayOf(0xD0.toByte(), 0x00))
                    }
                    output.flush()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun LinkedBlockingQueue<Message>.until(what: String, test: (Message) -> Boolean): Message {
        val end = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < end) {
            val next = poll(end - System.currentTimeMillis(), TimeUnit.MILLISECONDS) ?: break
            if (test(next)) return next
        }
        throw AssertionError("Never published: $what")
    }

    @Test
    fun publishesTakesCommandsAndReconnects() {
        val buds = DemoBuds()
        val settings = HaSettings(node).apply {
            host("127.0.0.1"); port(broker.localPort.toString()); username("ha"); password("secret")
        }
        val earbuds = MutableStateFlow<Buds?>(buds)
        val bridge = HomeAssistant(
            settings, earbuds, { "AA:BB:CC:DD:EE:FF" },
            { b, changes -> scope.launch { changes.forEach { b.apply(it) } } },
            keepAlive = 2,
        )
        bridge.start(scope)
        assertEquals("Off.", bridge.status.value.text)

        val connects = LinkedBlockingQueue<Raw>()
        val subscribed = LinkedBlockingQueue<List<String>>()
        val published = LinkedBlockingQueue<Message>()
        settings.connect(true)
        val first = broker.apply { soTimeout = 5_000 }.accept()
        serve(first, connects, subscribed, published)

        // CONNECT: 3.1.1, the login, a retained last will saying offline, and clean session.
        val connect = connects.poll(5, TimeUnit.SECONDS)!!
        assertEquals("MQTT", connect.text())
        assertEquals(4, connect.data.readUnsignedByte())
        assertEquals(0b1110_0110, connect.data.readUnsignedByte())
        assertEquals(2, connect.data.readUnsignedShort())
        assertTrue(connect.text().startsWith("blazbuds-"))
        assertEquals("blazbuds/aabbccddeeff/availability", connect.text())
        assertEquals("offline", connect.text())
        assertEquals("ha", connect.text())
        assertEquals("secret", connect.text())

        assertEquals(
            listOf("blazbuds/aabbccddeeff/mode/set", "blazbuds/aabbccddeeff/scene/set", "homeassistant/status"),
            subscribed.poll(5, TimeUnit.SECONDS),
        )
        val online = published.until("online") { it.topic == "blazbuds/aabbccddeeff/availability" }
        assertEquals("online", online.payload)
        assertTrue(online.retain)
        val mode = published.until("mode select") { it.topic == "homeassistant/select/blazbuds_aabbccddeeff/listening_mode/config" }
        assertTrue(mode.retain)
        val state = published.until("state") { it.topic == "blazbuds/aabbccddeeff/state" }
        assertTrue("\"mode\":\"Noise cancelling\"" in state.payload, state.payload)
        assertTrue(bridge.status.value.good, bridge.status.value.text)

        // Home Assistant picks Transparency: the earbuds change, and the new state comes back.
        first.getOutputStream().apply { write(Mqtt.publish("blazbuds/aabbccddeeff/mode/set", "Transparency".encodeToByteArray(), retain = false)); flush() }
        val changed = published.until("Transparency state") { it.topic == "blazbuds/aabbccddeeff/state" && "Transparency" in it.payload }
        assertTrue("\"scene\":null" in changed.payload, changed.payload)
        assertEquals(ListeningMode.Transparency, buds.state.value.sound?.mode)

        // A change made in the app shows up too.
        runBlocking { buds.apply(Change.Mode(ListeningMode.Normal)) }
        published.until("Normal state") { it.topic == "blazbuds/aabbccddeeff/state" && "\"mode\":\"Normal\"" in it.payload }

        // The broker drops the link: the app says so and comes back by itself.
        first.close()
        val end = System.currentTimeMillis() + 5_000
        while (!bridge.status.value.trouble && System.currentTimeMillis() < end) Thread.sleep(20)
        assertTrue(bridge.status.value.trouble, bridge.status.value.text)
        assertTrue("Trying again" in bridge.status.value.text, bridge.status.value.text)
        val second = broker.accept()
        serve(second, connects, subscribed, published)
        assertNotNull(connects.poll(5, TimeUnit.SECONDS))
        published.until("state again") { it.topic == "blazbuds/aabbccddeeff/state" && "Normal" in it.payload }

        // Switched off: offline first, then a goodbye, so the broker doesn't need the last will.
        published.clear()
        settings.connect(false)
        assertEquals("offline", published.until("offline") { it.topic == "blazbuds/aabbccddeeff/availability" }.payload)
        while (bridge.status.value.text != "Off." && System.currentTimeMillis() < end + 5_000) Thread.sleep(20)
        assertEquals("Off.", bridge.status.value.text)
        second.close()
    }

    @Test
    fun wrongLoginIsSaidPlainly() {
        val settings = HaSettings(node).apply { host("127.0.0.1"); port(broker.localPort.toString()); username("ha"); password("wrong") }
        val bridge = HomeAssistant(settings, MutableStateFlow<Buds?>(DemoBuds()), { "AA:BB:CC:DD:EE:FF" }, { _, _ -> })
        bridge.start(scope)
        settings.connect(true)
        broker.apply { soTimeout = 5_000 }.accept().use { peer ->
            assertEquals(Mqtt.CONNECT, read(peer.getInputStream()).type)
            peer.getOutputStream().apply { write(byteArrayOf(0x20, 0x02, 0x00, 0x04)); flush() }
            val end = System.currentTimeMillis() + 5_000
            while (!bridge.status.value.trouble && System.currentTimeMillis() < end) Thread.sleep(20)
        }
        assertEquals("The broker turned down the username or password. Trying again in 2 s.", bridge.status.value.text)
    }
}

package com.blazify.buds.desktop

import com.blazify.buds.core.demo.DemoBuds
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.ListeningMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Against a real broker, with demo earbuds: BLAZBUDS_LIVE_MQTT=host:port:user:password ./gradlew :desktop:test
 * --tests '*LiveBrokerTest*' --rerun. Skipped without it.
 */
class LiveBrokerTest {
    @Test
    fun roundTrip() {
        val live = System.getenv("BLAZBUDS_LIVE_MQTT")?.split(":") ?: return
        val (host, port) = live
        val user = live.getOrNull(2).orEmpty()
        val password = live.getOrNull(3).orEmpty()
        val node = Preferences.userRoot().node("blazbuds-test/live-" + System.nanoTime())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val buds = DemoBuds()
            val settings = HaSettings(node).apply { host(host); port(port); username(user); password(password) }
            val bridge = HomeAssistant(settings, MutableStateFlow<Buds?>(buds), { "AA:BB:CC:DD:EE:FF" }, { b, c -> scope.launch { c.forEach { b.apply(it) } } })
            bridge.start(scope)
            settings.connect(true)
            val end = System.currentTimeMillis() + 10_000
            while (!bridge.status.value.good && System.currentTimeMillis() < end) Thread.sleep(50)
            assertTrue(bridge.status.value.good, bridge.status.value.text)

            // A second client, as Home Assistant would be: the retained discovery and state are waiting for it.
            val seen = ConcurrentHashMap<String, String>()
            val ha = MqttClient(host, port.toInt(), "ha-test", user.ifEmpty { null }, password.ifEmpty { null }, null) { t, p -> seen[t] = p }
            ha.connect()
            ha.subscribe(listOf("homeassistant/+/blazbuds_aabbccddeeff/+/config", "blazbuds/aabbccddeeff/#"))
            while (seen.size < 8 && System.currentTimeMillis() < end) Thread.sleep(50)
            assertEquals("online", seen["blazbuds/aabbccddeeff/availability"])
            assertTrue(seen.containsKey("homeassistant/select/blazbuds_aabbccddeeff/listening_mode/config"), seen.keys.toString())
            assertTrue("\"mode\":\"Noise cancelling\"" in seen.getValue("blazbuds/aabbccddeeff/state"))

            ha.publish("blazbuds/aabbccddeeff/mode/set", "Transparency", retain = false)
            while ("Transparency" !in seen["blazbuds/aabbccddeeff/state"].orEmpty() && System.currentTimeMillis() < end) Thread.sleep(50)
            assertEquals(ListeningMode.Transparency, buds.state.value.sound?.mode)
            assertTrue("\"mode\":\"Transparency\"" in seen.getValue("blazbuds/aabbccddeeff/state"))
            println("LIVE: ${seen.size} topics seen; state ${seen["blazbuds/aabbccddeeff/state"]}")

            settings.connect(false)
            while (seen["blazbuds/aabbccddeeff/availability"] != "offline" && System.currentTimeMillis() < end) Thread.sleep(50)
            assertEquals("offline", seen["blazbuds/aabbccddeeff/availability"])
            ha.close(graceful = true)
        } finally {
            scope.cancel()
            node.removeNode()
        }
    }
}

package com.blazify.buds.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Scene
import com.blazify.buds.ui.VERSION
import com.blazify.buds.ui.blocks.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.prefs.Preferences

/** Where the broker is and the login for it, as the bridge uses them. */
data class BrokerConfig(val host: String, val port: Int, val username: String, val password: String)

/**
 * Settings › Home Assistant, kept on this computer: the broker, the login, and whether to connect. Off until switched
 * on. The fields can be edited only while it is off, so a half-typed address is never tried.
 */
class HaSettings(private val store: Preferences = Preferences.userRoot().node("blazbuds/home_assistant")) {
    var host by mutableStateOf(store.get(HOST, ""))
        private set
    var port by mutableStateOf(store.get(PORT, "1883"))
        private set
    var username by mutableStateOf(store.get(USERNAME, ""))
        private set
    var password by mutableStateOf(store.get(PASSWORD, ""))
        private set
    var on by mutableStateOf(store.getBoolean(ON, false))
        private set

    /** This computer's own name with the broker, made once. */
    val clientId: String = store.get(CLIENT_ID, null) ?: ("blazbuds-" + java.util.UUID.randomUUID().toString().take(8)).also { store.put(CLIENT_ID, it) }

    private val config = MutableStateFlow(current())

    /** What the bridge should connect to, or null while off. */
    val active: StateFlow<BrokerConfig?> = config

    private fun current(): BrokerConfig? = if (!on || host.isBlank()) null else
        BrokerConfig(host.trim(), port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 1883, username.trim(), password)

    fun host(value: String) { host = value; store.put(HOST, value) }
    fun port(value: String) { port = value.filter { it.isDigit() }.take(5); store.put(PORT, port) }
    fun username(value: String) { username = value; store.put(USERNAME, value) }
    fun password(value: String) { password = value; store.put(PASSWORD, value) }

    val canConnect: Boolean get() = host.isNotBlank() && (port.toIntOrNull() ?: 0) in 1..65535

    fun connect(value: Boolean) {
        on = value && canConnect
        store.putBoolean(ON, on)
        config.value = current()
    }

    private companion object {
        const val HOST = "host"
        const val PORT = "port"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val ON = "on"
        const val CLIENT_ID = "client_id"
    }
}

/**
 * The topics and Home Assistant's MQTT discovery messages for one pair: one device, "Blazbuds – <name>", with the
 * batteries, the listening mode and the scenes to pick, and whether the earbuds are connected.
 */
object HaDiscovery {
    const val PREFIX = "homeassistant"
    const val HA_STATUS = "$PREFIX/status"

    /** A pair's id in topics: its Bluetooth address in plain hex, so it stays the same across names and restarts. */
    fun id(address: String): String = address.lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "earbuds" }

    fun base(id: String) = "blazbuds/$id"
    fun availability(id: String) = "${base(id)}/availability"
    fun state(id: String) = "${base(id)}/state"
    fun modeCommand(id: String) = "${base(id)}/mode/set"
    fun sceneCommand(id: String) = "${base(id)}/scene/set"

    /** Discovery topic to config, in order. An empty map entry list means nothing is known yet. */
    fun configs(id: String, s: BudsState): Map<String, String> {
        val device = linkedMapOf<String, Any?>(
            "identifiers" to listOf("blazbuds_$id"),
            "name" to "Blazbuds – ${s.name}",
            "model" to s.model.ifBlank { null },
            "sw_version" to s.firmware,
        ).filterValues { it != null }
        val origin = mapOf("name" to "Blazbuds", "sw" to VERSION, "url" to "https://github.com/rajendra7169/blazbuds")
        fun entity(component: String, key: String, fields: Map<String, Any?>): Pair<String, String> {
            val config = linkedMapOf<String, Any?>(
                "unique_id" to "blazbuds_${id}_$key",
                "availability_topic" to availability(id),
                "state_topic" to state(id),
            )
            config += fields
            config["device"] = device
            config["origin"] = origin
            return "$PREFIX/$component/blazbuds_$id/$key/config" to Json.write(config)
        }
        fun battery(key: String, name: String) = entity(
            "sensor", key,
            mapOf(
                "name" to name,
                "device_class" to "battery",
                "unit_of_measurement" to "%",
                "state_class" to "measurement",
                "value_template" to "{{ value_json.$key }}",
            ),
        )
        return buildList {
            if (s.overall != null && s.left == null && s.right == null) {
                add(battery("battery", "Battery"))
            } else {
                add(battery("left", "Left battery"))
                add(battery("right", "Right battery"))
                add(battery("case", "Case battery"))
            }
            add(
                entity(
                    "binary_sensor", "connected",
                    mapOf(
                        "name" to "Connected",
                        "device_class" to "connectivity",
                        "value_template" to "{{ 'ON' if value_json.connected else 'OFF' }}",
                    ),
                ),
            )
            s.sound?.let { sound ->
                add(
                    entity(
                        "select", "listening_mode",
                        mapOf(
                            "name" to "Listening mode",
                            "icon" to "mdi:headphones",
                            "command_topic" to modeCommand(id),
                            "options" to sound.options.modes.map { it.label() },
                            "value_template" to "{{ value_json.mode }}",
                        ),
                    ),
                )
                add(
                    entity(
                        "select", "scene",
                        mapOf(
                            "name" to "Scene",
                            "icon" to "mdi:playlist-music",
                            "command_topic" to sceneCommand(id),
                            "options" to Scene.DEFAULTS.map { it.name },
                            "value_template" to "{{ value_json.scene }}",
                        ),
                    ),
                )
            }
        }.toMap(linkedMapOf())
    }

    /** The one state message every entity reads its value from; missing values are null and show as unknown. */
    fun statePayload(s: BudsState, lastCase: Battery? = null): String {
        val case = s.case ?: lastCase?.takeIf { s.isReady }
        return Json.write(
            linkedMapOf(
                "connected" to s.isReady,
                "link" to s.link.name.lowercase(),
                "left" to s.left?.percent,
                "right" to s.right?.percent,
                "case" to case?.percent,
                "battery" to s.overall?.percent,
                "left_charging" to s.left?.charging,
                "right_charging" to s.right?.charging,
                "case_charging" to case?.charging,
                "mode" to s.sound?.mode?.label(),
                "scene" to Scene.DEFAULTS.firstOrNull { it.matches(s) }?.name,
            ),
        )
    }

    /** The changes a command from Home Assistant asks for, or null if it names nothing these earbuds have. */
    fun command(id: String, topic: String, payload: String, s: BudsState): List<Change>? {
        val word = payload.trim()
        return when (topic) {
            modeCommand(id) -> {
                val mode = ListeningMode.entries.firstOrNull { it.label().equals(word, true) } ?: ModeWords.parse(word)
                mode?.takeIf { s.sound?.options?.modes?.contains(it) == true }?.let { listOf(Change.Mode(it)) }
            }
            sceneCommand(id) -> Scene.DEFAULTS.firstOrNull { it.name.equals(word, true) || it.id.equals(word, true) }
                ?.takeIf { s.sound != null }?.changes()
            else -> null
        }
    }
}

/** A line for Settings: what the bridge is doing, and whether that's good news. */
data class HaStatus(val text: String, val good: Boolean = false, val trouble: Boolean = false)

/**
 * Shows the chosen earbuds in Home Assistant over MQTT while Settings › Home Assistant is on: publishes discovery once
 * connected, the state whenever it changes, takes the mode and scene from Home Assistant, pings to keep the link, and
 * reconnects after a drop, waiting longer each time up to a minute. Everything runs on Dispatchers.IO.
 */
class HomeAssistant(
    private val settings: HaSettings,
    private val earbuds: StateFlow<Buds?>,
    private val address: () -> String?,
    /** Runs changes the way a tap in the app does. */
    private val apply: (Buds, List<Change>) -> Unit,
    private val lastCase: (String) -> Battery? = { null },
    private val keepAlive: Int = 30,
) {
    private val shown = MutableStateFlow(HaStatus(OFF))
    val status: StateFlow<HaStatus> = shown

    fun start(scope: CoroutineScope) = scope.launch(Dispatchers.IO) {
        settings.active.collectLatest { config ->
            if (config == null) {
                shown.value = HaStatus(OFF)
                return@collectLatest
            }
            earbuds.collectLatest { buds ->
                if (buds == null) {
                    shown.value = HaStatus("Waiting for you to choose your earbuds.")
                    return@collectLatest
                }
                val id = HaDiscovery.id(address() ?: buds.state.value.name)
                var wait = 2_000L
                while (true) {
                    shown.value = HaStatus("Connecting to ${config.host}:${config.port}…")
                    val why = try {
                        session(config, buds, id) { wait = 2_000L }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        e
                    }
                    shown.value = HaStatus("${explain(why, config)} Trying again in ${wait / 1000} s.", trouble = true)
                    delay(wait)
                    wait = (wait * 2).coerceAtMost(60_000L)
                }
            }
        }
    }

    /** One connection, from connect until it drops; returns why it ended. Cancelled, it says goodbye and goes offline. */
    private suspend fun session(config: BrokerConfig, buds: Buds, id: String, connected: () -> Unit): Throwable = coroutineScope {
        val incoming = Channel<Pair<String, String>>(64)
        val availability = HaDiscovery.availability(id)
        val client = MqttClient(
            config.host, config.port, settings.clientId,
            config.username.ifEmpty { null }, config.password.ifEmpty { null },
            Mqtt.Will(availability, "offline".encodeToByteArray(), retain = true),
            keepAlive,
        ) { topic, payload -> incoming.trySend(topic to payload) }
        try {
            client.connect()
            connected()
            client.subscribe(listOf(HaDiscovery.modeCommand(id), HaDiscovery.sceneCommand(id), HaDiscovery.HA_STATUS))
            client.publish(availability, "online", retain = true)
            val publisher = Publisher(client, id)
            shown.value = HaStatus("Connected. In Home Assistant: Blazbuds – ${buds.state.value.name}.", good = true)
            val jobs = listOf(
                launch {
                    buds.state.collect { state ->
                        publisher.show(state, lastCase(state.name))
                        shown.value = HaStatus("Connected. In Home Assistant: Blazbuds – ${state.name}.", good = true)
                    }
                },
                launch {
                    for ((topic, payload) in incoming) {
                        val state = buds.state.value
                        if (topic == HaDiscovery.HA_STATUS) {
                            // Home Assistant restarted: tell it about the earbuds again.
                            if (payload.trim() == "online") publisher.show(state, lastCase(state.name), again = true)
                        } else {
                            HaDiscovery.command(id, topic, payload, state)?.let { apply(buds, it) }
                        }
                    }
                },
                launch {
                    while (true) {
                        delay(keepAlive * 500L)
                        client.ping()
                    }
                },
            )
            val why = client.closed.await() ?: IOException("The broker closed the connection.")
            jobs.forEach { it.cancel() }
            why
        } finally {
            withContext(NonCancellable) {
                if (!client.closed.isCompleted) {
                    runCatching { client.publish(availability, "offline", retain = true) }
                    client.close(graceful = true)
                }
            }
        }
    }

    /** Publishes discovery when it changes and the state when it changes, nothing twice. */
    private class Publisher(private val client: MqttClient, private val id: String) {
        private var configs: Map<String, String> = emptyMap()
        private var last: String? = null

        fun show(state: BudsState, lastCase: Battery?, again: Boolean = false) {
            // While the earbuds are away they report less; keep what Home Assistant was told until they're back.
            if (state.isReady || configs.isEmpty() || again) {
                val next = HaDiscovery.configs(id, state).let { if (!state.isReady && configs.isNotEmpty()) configs else it }
                // An entity these earbuds no longer have is removed with an empty retained config.
                configs.keys.filter { it !in next }.forEach { client.publish(it, "", retain = true) }
                next.forEach { (topic, payload) -> if (again || configs[topic] != payload) client.publish(topic, payload, retain = true) }
                if (again) client.publish(HaDiscovery.availability(id), "online", retain = true)
                configs = next
            }
            val payload = HaDiscovery.statePayload(state, lastCase)
            if (again || payload != last) {
                client.publish(HaDiscovery.state(id), payload, retain = true)
                last = payload
            }
        }
    }

    private fun explain(why: Throwable, config: BrokerConfig): String = when (why) {
        is MqttRefused -> why.message ?: "The broker turned the connection down."
        is UnknownHostException -> "Can't find ${config.host}. Check the broker's address."
        is ConnectException -> "Can't reach ${config.host}:${config.port}. Is the broker running?"
        is SocketTimeoutException -> "${config.host}:${config.port} didn't answer."
        else -> "Lost the broker${why.message?.let { ": $it" } ?: "."}".let { if (it.endsWith(".")) it else "$it." }
    }

    private companion object {
        const val OFF = "Off."
    }
}

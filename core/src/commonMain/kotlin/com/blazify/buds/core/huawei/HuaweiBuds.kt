package com.blazify.buds.core.huawei

import com.blazify.buds.core.hex2
import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Host
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Multipoint
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import com.blazify.buds.core.model.Link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Huawei and Honor earbuds (Honor Choice Earbuds X7e Active checked on the real thing): battery for each earbud and the
 * case in whole percent, which is charging, and the model, firmware and serial; then whatever else the pair answers
 * for: noise control, touch controls, sound presets, pause when taken out, and low-latency game mode. Each of those is
 * asked for on connecting and shown only if the earbuds give a real answer, so every model gets just what it has.
 * They talk on a plain serial-port channel; some have a second one that speaks something else, so each way in
 * [opens] is tried until one answers in this protocol.
 */
class HuaweiBuds(
    private val name: String,
    private val scope: CoroutineScope,
    private val opens: List<suspend () -> Connection>,
    private val log: (String) -> Unit = {},
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, "", Link.Connecting))
    private val honorChoice = Regex("^(Earbuds X\\d|HONOR)", RegexOption.IGNORE_CASE).containsMatchIn(name)
    override val state: StateFlow<BudsState> = flow
    private var connection: Connection? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val answers = Channel<HuaweiPacket>(Channel.UNLIMITED)

    // What the earbuds have said about each setting; null until (unless) they answer.
    private var noise: Int? = null
    private val taps = mutableMapOf<Pair<Ear, Press>, Int>()
    private val tapChoices = mutableMapOf<Press, List<Int>>()
    private var presets: List<Int> = emptyList()
    private var preset: Int? = null
    private var autoPause: Boolean? = null
    private var lowLatency: Boolean? = null

    /** Dual-connect: on or off, and the devices the earbuds remember, by their place in the earbuds' list. */
    private var twoDevices: Boolean? = null
    private val hosts = mutableMapOf<Int, Host>()

    /**
     * Honor Choice earbuds keep their settings as numbered values in groups (EQ, taps, holds, switches), read with
     * `01 3A` and written with `01 39`; recorded from Honor's own app talking to the X7e Active. Empty for pairs
     * that don't answer it.
     */
    private val props = mutableMapOf<Pair<Int, Int>, Int>()
    @kotlin.concurrent.Volatile private var watching = true

    fun start() {
        if (runner != null) return
        runner = scope.launch {
            var wait = 2_000L
            while (isActive) {
                flow.update { if (it.link == Link.Lost) it else it.copy(link = Link.Connecting) }
                try {
                    session()
                    wait = 2_000L
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    log("link: ${e.message}")
                }
                connection?.close()
                connection = null
                flow.update { it.copy(link = Link.Lost) }
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    /** Opens the first way in that answers in this protocol, then keeps the state fresh until the link drops. */
    private suspend fun session() {
        var link: Connection? = null
        var reading: Job? = null
        for ((index, open) in opens.withIndex()) {
            val tried = runCatching { withContext(Dispatchers.IO) { open() } }.getOrElse {
                log("way ${index + 1}: ${it.message}")
                null
            } ?: continue
            while (answers.tryReceive().isSuccess) Unit
            val job = scope.launch(Dispatchers.IO) { readLoop(tried) }
            send(tried, HuaweiPacket.ask(BATTERY_SERVICE, BATTERY, 1, 2, 3))
            val first = withTimeoutOrNull(2_500) { answers.receive() }
            if (first != null) {
                link = tried
                reading = job
                handle(first)
                log("way ${index + 1} answers")
                break
            }
            log("way ${index + 1}: no answer in this protocol")
            tried.close()
            job.cancel()
        }
        if (link == null || reading == null) throw LinkException("These earbuds don't answer")
        connection = link
        flow.update { it.copy(link = Link.Connected) }
        send(link, HuaweiPacket.ask(INFO_SERVICE, INFO, 1, 3, 7, 9, 10, 12, 15, 17))
        // Everything else this family can have; only what's answered shows.
        READS.forEach { send(link, it) }
        send(link, ASK_HOSTS)
        HONOR_PROPS.chunked(5).forEach { send(link, readProps(it)) }
        val handling = scope.launch {
            for (packet in answers) handle(packet)
        }
        val polling = scope.launch {
            while (true) {
                delay(30_000)
                if (watching) send(link, HuaweiPacket.ask(BATTERY_SERVICE, BATTERY, 1, 2, 3))
            }
        }
        try {
            reading.join()
        } finally {
            polling.cancel()
            handling.cancel()
            link.close()
        }
    }

    private suspend fun send(link: Connection, packet: HuaweiPacket) {
        log("-> $packet")
        withContext(Dispatchers.IO) { link.write(HuaweiPacket.encode(packet)) }
    }

    private fun readLoop(link: Connection) {
        val reader = HuaweiPacket.Reader()
        val buffer = ByteArray(2048)
        while (true) {
            val count = link.read(buffer)
            if (count < 0) return
            for (packet in reader.feed(buffer, count)) answers.trySend(packet)
        }
    }

    private fun handle(packet: HuaweiPacket) {
        if (packet.id != CHATTER) log("<- $packet")
        if (packet.failed) return
        when (packet.id) {
            (BATTERY_SERVICE shl 8) or BATTERY, (BATTERY_SERVICE shl 8) or BATTERY_PUSH -> {
                val levels = packet.field(2) ?: return
                val charging = packet.field(3)
                fun level(i: Int): Battery? {
                    val percent = levels.getOrNull(i)?.toInt()?.and(0xFF) ?: return null
                    // 0 means that one isn't heard from: an earbud away, or the case while both are out.
                    if (percent == 0 || percent > 100) return null
                    return Battery(percent, charging = charging?.getOrNull(i)?.toInt() == 1)
                }
                flow.update { it.copy(left = level(0), right = level(1), case = level(2), overall = packet.field(1)?.firstOrNull()?.toInt()?.and(0xFF)?.takeIf { p -> p in 1..100 }?.let { p -> Battery(p) }) }
            }
            PROP_READ -> {
                val blocks = packet.field(0x81) ?: return
                var at = 0
                while (at + 2 <= blocks.size) {
                    val size = blocks[at + 1].toInt() and 0xFF
                    val inner = blocks.copyOfRange(at + 2, minOf(blocks.size, at + 2 + size))
                    at += 2 + size
                    var k = 0
                    var group = -1
                    var prop = -1
                    var value: Int? = null
                    while (k + 2 <= inner.size) {
                        val type = inner[k].toInt() and 0xFF
                        val len = inner[k + 1].toInt() and 0xFF
                        val v = inner.copyOfRange(k + 2, minOf(inner.size, k + 2 + len))
                        val number = v.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
                        when (type) { 3 -> group = number; 4 -> prop = number; 5 -> if (v.isNotEmpty()) value = number }
                        k += 2 + len
                    }
                    if (group >= 0 && prop >= 0 && value != null) props[group to prop] = value!!
                }
                publishSettings()
            }
            NOISE_READ, NOISE_PUSH -> {
                val data = packet.field(1) ?: return
                // The read gives (strength, mode); a change made on the earbuds gives the mode alone.
                noise = (if (data.size >= 2) data[1] else data.firstOrNull() ?: return).toInt() and 0xFF
                if (packet.id == NOISE_PUSH) connection?.let { link -> scope.launch { runCatching { send(link, HuaweiPacket.ask(0x2B, 0x2A, 1, 2)) } } }
                publishSettings()
            }
            DOUBLE_READ, TRIPLE_READ, LONG_READ -> {
                val press = when (packet.id) { DOUBLE_READ -> Press.Double; TRIPLE_READ -> Press.Triple; else -> Press.Long }
                packet.field(1)?.firstOrNull()?.let { taps[Ear.Left to press] = it.toInt() }
                packet.field(2)?.firstOrNull()?.let { taps[Ear.Right to press] = it.toInt() }
                packet.field(3)?.takeIf { it.isNotEmpty() }?.let { list -> tapChoices[press] = list.map { it.toInt() } }
                publishSettings()
            }
            EQ_READ -> {
                packet.field(3)?.takeIf { it.isNotEmpty() }?.let { list -> presets = list.map { it.toInt() and 0xFF } }
                packet.field(2)?.firstOrNull()?.let { preset = it.toInt() and 0xFF }
                publishSettings()
            }
            DUAL_READ -> packet.field(1)?.takeIf { it.size == 1 }?.let { twoDevices = it[0].toInt() == 1; publishHosts() }
            DUAL_LIST -> host(packet)?.let { (index, host) -> hosts[index] = host; publishHosts() }
            DUAL_CHANGED -> connection?.let { link -> scope.launch { runCatching { askHosts(link) } } }
            PAUSE_READ -> packet.field(1)?.firstOrNull()?.let { autoPause = it.toInt() == 1; publishSettings() }
            LATENCY_READ -> (packet.field(2) ?: packet.field(1))?.firstOrNull()?.let { lowLatency = it.toInt() == 1; publishSettings() }
            (INFO_SERVICE shl 8) or INFO -> {
                fun text(type: Int) = packet.field(type)?.decodeToString()?.trim()?.takeIf { it.isNotEmpty() }
                flow.update {
                    it.copy(
                        model = text(17) ?: text(10) ?: it.model,
                        firmware = text(7) ?: text(1),
                        serial = text(9),
                    )
                }
            }
        }
    }

    override suspend fun refresh() {
        val link = connection
        if (link == null) {
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        runCatching { send(link, HuaweiPacket.ask(BATTERY_SERVICE, BATTERY, 1, 2, 3)) }
    }

    /** Dual-connect as the Device tab shows it; nothing until the earbuds say whether they have it. */
    private fun publishHosts() {
        val on = twoDevices ?: return
        flow.update { it.copy(multipoint = Multipoint(on, hosts.entries.sortedBy { it.key }.map { it.value })) }
    }

    /** Asks for the remembered devices again, starting the list afresh. */
    private suspend fun askHosts(link: Connection) {
        hosts.clear()
        send(link, HuaweiPacket.ask(0x2B, 0x2F, 1))
        send(link, ASK_HOSTS)
    }

    /** Puts what the earbuds have answered into the state, as the screens know it. */
    private fun publishSettings() {
        if (props.isNotEmpty()) return publishHonor()
        val sound = noise?.let { mode ->
            Sound(
                mode = when (mode) { 1 -> ListeningMode.NoiseCancelling; 2 -> ListeningMode.Transparency; else -> ListeningMode.Normal },
                options = SoundOptions(cancelStyles = emptyList(), transparencyStyles = false, wind = false, environment = false, cycle = false),
            )
        }
        val equalizer = if (presets.isEmpty()) null else Equalizer(
            presetId = preset ?: presets.first(),
            presets = presets.map { EqPreset(it, presetName(it), emptyList()) },
            frequencies = emptyList(),
            left = emptyList(),
            custom = false,
        )
        val controls = if (taps.isEmpty()) null else TouchControls(
            actions = taps.mapValues { (key, code) -> action(key.second, code) },
            available = TAP_ACTIONS,
            availableFor = buildMap {
                put(Press.Long, listOf(TouchAction.ListeningMode, TouchAction.None))
                tapChoices.forEach { (press, codes) -> if (press != Press.Long) put(press, codes.map { action(press, it) }.distinct()) }
            },
        )
        val toggles = buildMap {
            autoPause?.let { put(Toggle.WearDetection, it) }
            lowLatency?.let { put(Toggle.GamingMode, it) }
        }
        flow.update { it.copy(sound = sound, equalizer = equalizer, controls = controls, toggles = toggles) }
    }

    /** The Honor Choice settings, as the screens know them. */
    private fun publishHonor() {
        val equalizer = props[EQ]?.let { current ->
            Equalizer(current, HONOR_PRESETS.map { (id, name) -> EqPreset(id, name, emptyList()) }, emptyList(), emptyList(), custom = false)
        }
        val actions = buildMap {
            for ((key, slot) in HONOR_TAPS) props[slot]?.let { code -> HONOR_ACTIONS[key.second]?.entries?.firstOrNull { it.value == code }?.key?.let { put(key, it) } }
        }
        val controls = if (actions.isEmpty()) null else TouchControls(
            actions = actions,
            available = HONOR_ACTIONS.getValue(Press.Double).keys.toList(),
            availableFor = HONOR_ACTIONS.mapValues { it.value.keys.toList() },
        )
        val toggles = buildMap {
            HONOR_SWITCHES.forEach { (toggle, slot) -> props[slot]?.let { put(toggle, it == 1) } }
        }
        // Honor's own app offers no noise control on these, so none is shown here either.
        flow.update { it.copy(sound = null, equalizer = equalizer, controls = controls, toggles = toggles) }
    }

    private fun readProps(slots: List<Pair<Int, Int>>): HuaweiPacket {
        val blocks = slots.flatMap { (group, prop) ->
            listOf<Byte>(0x02, 0x0E, 0x03, 0x04) + int4(group) + listOf<Byte>(0x04, 0x04) + int4(prop) + listOf<Byte>(0x05, 0x00)
        }
        return HuaweiPacket(0x01, 0x3A, mapOf(1 to blocks.toByteArray()))
    }

    private fun writeProp(slot: Pair<Int, Int>, value: Int) =
        HuaweiPacket(0x01, 0x39, mapOf(1 to int4(slot.first).toByteArray(), 2 to int4(slot.second).toByteArray(), 3 to int4(value).toByteArray()))

    private fun int4(n: Int) = listOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

    private suspend fun honorChange(link: Connection, change: Change): Boolean {
        val (slot, value) = when (change) {
            is Change.Preset -> EQ to change.id
            is Change.Touch -> {
                val slot = HONOR_TAPS[change.ear to change.press] ?: return true
                val value = HONOR_ACTIONS[change.press]?.get(change.action) ?: return true
                slot to value
            }
            is Change.Switch -> (HONOR_SWITCHES[change.toggle] ?: return true) to (if (change.on) 1 else 0)
            else -> return false
        }
        props[slot] = value
        publishHonor()
        set(link, writeProp(slot, value), readProps(listOf(slot)))
        return true
    }

    private fun action(press: Press, code: Int): TouchAction = when {
        press == Press.Long -> if (code == LONG_NOISE) TouchAction.ListeningMode else TouchAction.None
        else -> when (code) {
            1 -> TouchAction.PlayPause
            2 -> TouchAction.NextSong
            7 -> TouchAction.PreviousSong
            0 -> TouchAction.VoiceAssistant
            else -> TouchAction.None
        }
    }

    private fun code(press: Press, action: TouchAction): Int? = if (press == Press.Long) {
        when (action) { TouchAction.ListeningMode -> LONG_NOISE; TouchAction.None -> -1; else -> null }
    } else when (action) {
        TouchAction.PlayPause -> 1
        TouchAction.NextSong -> 2
        TouchAction.PreviousSong -> 7
        TouchAction.VoiceAssistant -> 0
        TouchAction.None -> -1
        else -> null
    }

    override suspend fun apply(change: Change) {
        val link = connection ?: return
        if (props.isNotEmpty() && honorChange(link, change)) return
        when (change) {
            is Change.TwoDevices -> {
                twoDevices = change.on
                publishHosts()
                set(link, HuaweiPacket(0x2B, 0x2E, mapOf(1 to byteArrayOf(if (change.on) 1 else 0))), HuaweiPacket.ask(0x2B, 0x2F, 1))
            }
            is Change.HostConnection -> hostCommand(link, if (change.connect) HOST_CONNECT else HOST_DISCONNECT, change.address)
            is Change.Forget -> hostCommand(link, HOST_FORGET, change.address)
            is Change.Mode -> {
                val mode = when (change.mode) { ListeningMode.NoiseCancelling -> 1; ListeningMode.Transparency -> 2; ListeningMode.Normal -> 0 }
                noise = mode
                publishSettings()
                // Honor Choice earbuds take the mode alone (checked on the X7e Active); Huawei's own FreeBuds take it
                // with a strength after it.
                val value = if (honorChoice) byteArrayOf(mode.toByte()) else byteArrayOf(mode.toByte(), 0xFF.toByte())
                set(link, HuaweiPacket(0x2B, 0x04, mapOf(1 to value)), HuaweiPacket.ask(0x2B, 0x2A, 1, 2))
            }
            is Change.Preset -> {
                preset = change.id
                publishSettings()
                set(link, HuaweiPacket(0x2B, 0x49, mapOf(1 to byteArrayOf(change.id.toByte()))), HuaweiPacket.ask(0x2B, 0x4A, 1, 2, 3, 4, 5, 6, 7, 8))
            }
            is Change.Touch -> {
                val value = code(change.press, change.action) ?: return
                val (write, read) = when (change.press) {
                    Press.Double -> (0x01 to 0x1F) to HuaweiPacket.ask(0x01, 0x20, 1, 2, 3)
                    Press.Triple -> (0x01 to 0x25) to HuaweiPacket.ask(0x01, 0x26, 1, 2, 3)
                    Press.Long -> (0x2B to 0x16) to HuaweiPacket.ask(0x2B, 0x17, 1, 2, 3)
                    Press.Single -> return
                }
                taps[change.ear to change.press] = value
                publishSettings()
                val field = if (change.ear == Ear.Left) 1 else 2
                set(link, HuaweiPacket(write.first, write.second, mapOf(field to byteArrayOf(value.toByte()))), read)
            }
            is Change.Switch -> when (change.toggle) {
                Toggle.WearDetection -> {
                    autoPause = change.on
                    publishSettings()
                    set(link, HuaweiPacket(0x2B, 0x10, mapOf(1 to byteArrayOf(if (change.on) 1 else 0))), HuaweiPacket.ask(0x2B, 0x11, 1))
                }
                Toggle.GamingMode -> {
                    lowLatency = change.on
                    publishSettings()
                    set(link, HuaweiPacket(0x2B, 0x6C, mapOf(1 to byteArrayOf(if (change.on) 1 else 0))), HuaweiPacket.ask(0x2B, 0x6C, 2))
                }
                else -> {}
            }
            else -> {}
        }
    }

    /** Connects, disconnects or forgets one remembered device, then lists them again once the earbuds have done it. */
    private suspend fun hostCommand(link: Connection, command: Int, address: String) {
        val mac = macBytes(address) ?: return
        runCatching {
            send(link, HuaweiPacket(0x2B, 0x33, mapOf(command to mac)))
            delay(1_500)
            askHosts(link)
        }
    }

    /** Sends a change, then asks again so the screen shows what the earbuds really did. */
    private suspend fun set(link: Connection, change: HuaweiPacket, readBack: HuaweiPacket) {
        runCatching {
            send(link, change)
            delay(400)
            send(link, readBack)
        }
    }

    override fun watch(on: Boolean) {
        watching = on
        if (on) wake.trySend(Unit)
    }

    override fun close() {
        val job = runner
        runner = null
        connection?.close()
        scope.launch { job?.cancelAndJoin() }
    }

    companion object {
        const val INFO_SERVICE = 0x01
        const val INFO = 0x07
        const val BATTERY_SERVICE = 0x01
        const val BATTERY = 0x08
        const val BATTERY_PUSH = 0x27

        private const val PROP_READ = 0x013A
        private val EQ = 3 to 4
        private val HONOR_PRESETS = listOf(0 to "Default", 1 to "Bass boost", 2 to "Treble boost")
        private val HONOR_TAPS = mapOf(
            (Ear.Left to Press.Double) to (4 to 2), (Ear.Right to Press.Double) to (4 to 3),
            (Ear.Left to Press.Triple) to (4 to 5), (Ear.Right to Press.Triple) to (4 to 6),
            (Ear.Left to Press.Long) to (5 to 2), (Ear.Right to Press.Long) to (5 to 3),
        )

        /** What each kind of touch can do on Honor Choice earbuds, with the number for each. */
        private val HONOR_ACTIONS = mapOf(
            Press.Double to linkedMapOf(TouchAction.PlayPause to 1, TouchAction.NextSong to 2, TouchAction.PreviousSong to 3, TouchAction.VoiceAssistant to 0, TouchAction.None to 4),
            Press.Triple to linkedMapOf(TouchAction.PreviousSong to 1, TouchAction.NextSong to 0, TouchAction.None to 2),
            Press.Long to linkedMapOf(TouchAction.VolumeUp to 0, TouchAction.VolumeDown to 1, TouchAction.None to 2),
        )
        private val HONOR_SWITCHES = mapOf(Toggle.Surround to (6 to 0), Toggle.DynamicBass to (6 to 1), Toggle.GamingMode to (6 to 2))
        private val HONOR_PROPS = listOf(EQ) + HONOR_TAPS.values + HONOR_SWITCHES.values

        /** Something these earbuds send over and over that isn't about settings. */
        private const val CHATTER = 0x0A0D
        private const val NOISE_READ = 0x2B2A
        private const val NOISE_PUSH = 0x2B03
        private const val DOUBLE_READ = 0x0120
        private const val TRIPLE_READ = 0x0126
        private const val LONG_READ = 0x2B17
        private const val EQ_READ = 0x2B4A
        private const val PAUSE_READ = 0x2B11
        private const val LATENCY_READ = 0x2B6C

        // Dual-connect, as OpenFreebuds documents it for Huawei's FreeBuds: on/off read (2B 2F) and write (2B 2E),
        // the remembered devices one message each (2B 31), what to do with one (2B 33), and "something changed" (2B 36).
        private const val DUAL_READ = 0x2B2F
        private const val DUAL_LIST = 0x2B31
        private const val DUAL_CHANGED = 0x2B36
        private val ASK_HOSTS = HuaweiPacket.ask(0x2B, 0x31, 1)
        private const val HOST_CONNECT = 1
        private const val HOST_DISCONNECT = 2
        private const val HOST_FORGET = 3

        /**
         * One remembered device from a 2B 31 answer: its place in the list (3), its address (4), whether it is connected
         * (5: 0 not, 9 playing, anything else connected) and its name (9).
         */
        fun host(packet: HuaweiPacket): Pair<Int, Host>? {
            val mac = packet.field(4)?.takeIf { it.size == 6 } ?: return null
            val index = packet.field(3)?.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) } ?: return null
            val state = packet.field(5)?.firstOrNull()?.toInt()?.and(0xFF) ?: 0
            val address = mac.joinToString(":") { it.hex2().uppercase() }
            val name = packet.field(9)?.decodeToString()?.trim()?.takeIf { it.isNotEmpty() } ?: address
            return index to Host(name, address, connected = state > 0, playing = state == 9)
        }

        /** "AA:BB:CC:DD:EE:FF" (or without colons) as the six bytes the earbuds use. */
        fun macBytes(address: String): ByteArray? {
            val hex = address.filter { it.isLetterOrDigit() }
            if (hex.length != 12) return null
            return ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toIntOrNull(16)?.toByte() ?: return null }
        }

        /** Press and hold set to switch the noise control. */
        private const val LONG_NOISE = 10

        private val TAP_ACTIONS = listOf(TouchAction.PlayPause, TouchAction.NextSong, TouchAction.PreviousSong, TouchAction.VoiceAssistant, TouchAction.None)

        private val READS = listOf(
            HuaweiPacket.ask(0x2B, 0x2A, 1, 2),
            HuaweiPacket.ask(0x01, 0x20, 1, 2, 3),
            HuaweiPacket.ask(0x01, 0x26, 1, 2, 3),
            HuaweiPacket.ask(0x2B, 0x17, 1, 2, 3),
            HuaweiPacket.ask(0x2B, 0x4A, 1, 2, 3, 4, 5, 6, 7, 8),
            HuaweiPacket.ask(0x2B, 0x11, 1),
            HuaweiPacket.ask(0x2B, 0x6C, 2),
            HuaweiPacket.ask(0x2B, 0x2F, 1),
        )

        fun presetName(id: Int) = when (id) {
            1 -> "Default"
            2 -> "Bass boost"
            3 -> "Treble boost"
            9 -> "Voices"
            else -> "Sound $id"
        }

        /** The plain serial-port service these earbuds talk on. */
        const val SERIAL = "00001101-0000-1000-8000-00805f9b34fb"
    }
}

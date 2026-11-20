package com.blazify.buds.core.oneplus

import com.blazify.buds.core.link.Connection
import com.blazify.buds.core.link.LinkException
import com.blazify.buds.core.model.Battery
import com.blazify.buds.core.model.Buds
import com.blazify.buds.core.model.BudsState
import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Change
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.EqPreset
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.Link
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Multipoint
import com.blazify.buds.core.model.Press
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.Toggle
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TouchControls
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * OnePlus, OPPO and realme earbuds that HeyMelody or realme Link control (OnePlus Buds 3 and 4, Buds Pro 2 and 3,
 * Nord Buds, OPPO Enco X, Air and Free, realme Buds Air and T series, and the rest of [MELODY_MODELS]).
 *
 * On connecting the earbuds say which commands they take and which model they are; the model picks the noise
 * modes, presets and touch choices from the list, and each setting is then asked for and shown only if the earbuds
 * both take its command and answer it. So each model gets just what it has: battery for each earbud and the case,
 * noise control with its levels and the hold's cycle, presets and the earbuds' own custom curve, the touches,
 * in-ear detection, low-latency game mode, 3D sound, the bass lift, wind noise reduction, two devices at once, the
 * firmware, and a sound to find them. Every change is read back, as the earbuds can accept a change and not make it.
 *
 * [opens] are the ways in, tried in turn until one answers the handshake. On OnePlus, OPPO and realme phones the
 * system's own earbud settings may hold the channel; a refused channel then shows as busy.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class MelodyBuds(
    private val name: String,
    private val scope: CoroutineScope,
    private val opens: List<suspend () -> Connection>,
    private val log: (String) -> Unit = {},
    private val now: () -> Long = { kotlin.time.Clock.System.now().epochSeconds },
) : Buds {
    private val flow = MutableStateFlow(BudsState(name, "", Link.Connecting))
    override val state: StateFlow<BudsState> = flow
    private var connection: Connection? = null
    private var runner: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val sending = Mutex()
    private val writing = Mutex()
    private var number = 0
    private val waiting = mutableMapOf<Int, CompletableDeferred<MelodyMessage>>()
    @kotlin.concurrent.Volatile private var watching = true

    // What the earbuds have said; null until (unless) they answer.
    private var accepts: Melody.Accepts? = null
    private var productId: Int? = null
    private var model: MelodyModel? = null
    private var noise: MelodyNoise? = null
    private var noiseRaw: Int? = null
    private var lastLevel: Int? = null
    private var cycleMask: Int? = null
    private val switches = mutableMapOf<Int, Int>()
    private var eqId: Int? = null
    private var customs: List<Melody.CustomEq>? = null
    private var taps: List<Melody.Tap>? = null
    private var firmware: String? = null

    fun start() {
        if (runner != null) return
        runner = scope.launch {
            var wait = 2_000L
            while (isActive) {
                flow.update { if (it.link == Link.Busy || it.link == Link.Lost) it else it.copy(link = Link.Connecting) }
                val busy = try {
                    session()
                    wait = 2_000L
                    false
                } catch (e: LinkException) {
                    log("link: ${e.message}")
                    e.busy
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    log("link: $e")
                    false
                }
                connection?.close()
                connection = null
                waiting.values.forEach { it.cancel() }
                waiting.clear()
                flow.update { it.copy(link = if (busy) Link.Busy else Link.Lost) }
                withTimeoutOrNull(wait) { wake.receive() }
                wait = (wait * 2).coerceAtMost(20_000L)
            }
        }
    }

    /** Opens the first way in that answers the handshake, asks for everything, then keeps up until the link drops. */
    private suspend fun session() {
        var busy = false
        var link: Connection? = null
        var reading: Job? = null
        for ((index, open) in opens.withIndex()) {
            val tried = try {
                withContext(Dispatchers.IO) { open() }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e is LinkException && e.busy) busy = true
                log("way ${index + 1}: ${e.message}")
                continue
            }
            connection = tried
            val job = listen(tried)
            val hello = request(Melody.HANDSHAKE, timeout = 2_500)?.let { Melody.Accepts.of(it.payload) }
            if (hello != null) {
                accepts = hello
                link = tried
                reading = job
                log("way ${index + 1} answers")
                break
            }
            log("way ${index + 1}: no answer in this protocol")
            connection = null
            tried.close()
            job.cancel()
        }
        if (link == null || reading == null) throw LinkException("These earbuds don't answer", busy)
        try {
            productId = request(Melody.PRODUCT)?.let { Melody.productId(it.payload) }
            model = MelodyModel.find(name, productId)
            noise = model?.modes?.let(::MelodyNoise)?.takeIf { !it.isEmpty && has(Melody.SET_NOISE) }
            log("product ${productId?.let { hex4(it shr 8) + hex2(it) }}: ${model?.name ?: "not in the list"}")
            flow.update { it.copy(link = Link.Connected, model = model?.name ?: it.model, canRing = has(Melody.FIND) && model?.has(MelodyModel.NO_FIND) != true) }
            subscribe()
            readAll()
            val polling = scope.launch {
                while (true) {
                    delay(30_000)
                    if (watching) request(Melody.BATTERY)
                }
            }
            try {
                reading.join()
            } finally {
                polling.cancel()
            }
        } finally {
            link.close()
            reading.cancel()
        }
    }

    private fun has(command: Int) = accepts?.has(command) == true

    /** Asks for the news that keeps the screen current: battery, in-ear and noise mode, where the earbuds offer it. */
    private suspend fun subscribe() {
        val offered = request(Melody.OFFERED)?.payload?.takeIf { it.size >= 2 && it[0].toInt() == 0 }
            ?.let { p -> p.copyOfRange(2, minOf(p.size, 2 + (p[1].toInt() and 0xFF))).map { it.toInt() and 0xFF } }
        val codes = listOf(1, 2, 3).filter { offered == null || it in offered }
        if (codes.isEmpty()) return
        if (has(Melody.SUBSCRIBE)) send(Melody.SUBSCRIBE, byteArrayOf(codes.size.toByte()) + codes.map { it.toByte() })
        else codes.forEach { send(Melody.SUBSCRIBE_ONE, byteArrayOf(it.toByte())) }
    }

    private suspend fun readAll() {
        request(Melody.BATTERY)
        if (has(Melody.FIRMWARE)) request(Melody.FIRMWARE)
        request(Melody.SWITCHES, askList(askIds()))
        if (noise != null) {
            request(Melody.NOISE, byteArrayOf(1, 1))
            if (noise?.cycles == true && model?.holdMask == 0) request(Melody.NOISE, byteArrayOf(2, 1))
        }
        val m = model
        if (m != null && m.presets.isNotEmpty() && has(Melody.SET_EQ)) request(Melody.EQ)
        if (m?.has(MelodyModel.CUSTOM_EQ) == true && has(Melody.SAVE_EQ)) {
            if (!has(Melody.SET_EQ)) request(Melody.EQ)
            request(Melody.CUSTOM_EQS)
        }
        if (m != null && m.touchRows.isNotEmpty() && has(Melody.SET_TAPS)) request(Melody.TAPS)
        publish()
    }

    private fun askIds(): List<Int> = model?.askIds ?: listOf(0x05, Melody.WEAR, Melody.GAME)

    private fun askList(ids: List<Int>) = byteArrayOf(ids.size.toByte()) + ids.map { it.toByte() }

    /** Sends a command and waits for its answer, one at a time; null if none comes. */
    private suspend fun request(command: Int, payload: ByteArray = ByteArray(0), timeout: Long = 1_500): MelodyMessage? = sending.withLock {
        val answer = CompletableDeferred<MelodyMessage>()
        waiting[command or 0x8000] = answer
        if (!runCatching { send(command, payload) }.isSuccess) {
            waiting.remove(command or 0x8000)
            return null
        }
        withTimeoutOrNull(timeout) { answer.await() }.also { waiting.remove(command or 0x8000) }
    }

    private suspend fun send(command: Int, payload: ByteArray) {
        val link = connection ?: throw LinkException("Not connected")
        // Numbers 01 to BF are the app's own; the earbuds use FF for their news.
        number = number % 0xBF + 1
        log("-> " + MelodyMessage(command, number, payload))
        write(link, MelodyFrame.encode(command, number, payload))
    }

    private suspend fun write(link: Connection, bytes: ByteArray) = writing.withLock { withContext(Dispatchers.IO) { link.write(bytes) } }

    /** Reads the link until it closes, handling each message in the order it came. */
    private fun listen(link: Connection): Job = scope.launch {
        val inbox = Channel<MelodyMessage>(Channel.UNLIMITED)
        val handling = launch { for (message in inbox) handle(link, message) }
        try {
            withContext(Dispatchers.IO) {
                val reader = MelodyFrame.Reader()
                val buffer = ByteArray(2048)
                while (true) {
                    val count = link.read(buffer)
                    if (count < 0) break
                    reader.feed(buffer, count).forEach { inbox.trySend(it) }
                }
            }
        } finally {
            inbox.close()
            handling.cancel()
        }
    }

    private suspend fun handle(link: Connection, message: MelodyMessage) {
        log("<- $message")
        Melody.answer(message, now())?.let { runCatching { write(link, it) } }
        val p = message.payload
        val fine = message.at(0) == 0
        when (message.command) {
            Melody.BATTERY or 0x8000 -> if (fine) batteries(Melody.battery(p, 1))
            Melody.NEWS -> when (message.at(0)) {
                Melody.NEWS_BATTERY -> batteries(Melody.battery(p, 1))
                Melody.NEWS_NOISE -> noiseNews(p, 1)
                Melody.NEWS_GAME -> {
                    // Four older OPPO models put the state third.
                    val state = if (productId in LATE_GAME) message.at(3) else message.at(1)
                    if (state >= 0) switches[Melody.GAME] = state
                }
            }
            Melody.NOISE or 0x8000 -> if (fine) noiseNews(p, 1)
            Melody.SWITCHES or 0x8000 -> if (fine) switches += Melody.switches(p, 1)
            Melody.SWITCH_NEWS -> switches += Melody.switches(p, 0)
            Melody.EQ or 0x8000 -> if (fine && p.size >= 2) eqId = message.at(1)
            Melody.EQ_NEWS -> if (p.isNotEmpty()) eqId = message.at(0)
            Melody.CUSTOM_EQS or 0x8000 -> if (fine) Melody.customEqs(p, 1)?.let { customs = it }
            CUSTOM_EQ_NEWS -> Melody.customEqs(p, 0)?.let { customs = it }
            Melody.TAPS or 0x8000 -> Melody.taps(p)?.takeIf { it.isNotEmpty() }?.let { taps = it }
            Melody.FIRMWARE or 0x8000 -> Melody.firmware(p)?.let { firmware = it }
        }
        if (message.isAnswer) waiting[message.command]?.complete(message)
        publish()
    }

    /** The current mode (`01 01 <value>`) or the hold's cycle (`02 01 <mask>`), in an answer or the earbuds' news. */
    private fun noiseNews(p: ByteArray, from: Int) {
        if (p.size < from + 3) return
        val what = p[from].toInt() and 0xFF
        val kind = p[from + 1].toInt() and 0xFF
        val value = Melody.mask(p, from + 2)
        when {
            what == 1 && kind == 1 -> noiseRaw = value
            what == 2 && kind == 1 -> cycleMask = value
        }
    }

    /** Levels for the parts named; a part left out keeps its last level. */
    private fun batteries(levels: Map<Int, Battery>?) {
        if (levels == null) return
        flow.update { it.copy(left = levels[1] ?: it.left, right = levels[2] ?: it.right, case = levels[3] ?: it.case) }
    }

    /** Puts what the earbuds have answered into the state, as the screens know it. */
    private fun publish() {
        val canSwitch = has(Melody.SET_SWITCH)
        fun switch(id: Int): Boolean? = switches[id]?.takeIf { canSwitch }?.let { it == 1 }
        val toggles = buildMap {
            switch(Melody.WEAR)?.let { put(Toggle.WearDetection, it) }
            switch(Melody.GAME)?.let { put(Toggle.GamingMode, it) }
            if (spatialBySwitch()) switch(Melody.SPATIAL)?.let { put(Toggle.Surround, it) }
            switch(Melody.BASS)?.let { put(Toggle.DynamicBass, it) }
        }
        flow.update {
            it.copy(
                sound = sound(switch(Melody.WIND)),
                equalizer = equalizer(),
                controls = controls(),
                toggles = toggles,
                multipoint = switch(Melody.DUAL)?.let { on -> Multipoint(on) },
                firmware = firmware ?: it.firmware,
            )
        }
    }

    /**
     * 3D sound by its switch: realme models that use it, and OnePlus and OPPO ones whose earbuds make the effect
     * themselves. On the others the phone makes it, which only their makers' phones do, or it has its own command.
     */
    private fun spatialBySwitch(): Boolean {
        val m = model ?: return has(Melody.BUDS_SPATIAL) && !has(Melody.SPATIAL_TYPE)
        if (m.has(MelodyModel.SPATIAL_TOGGLE)) return true
        if (m.has(MelodyModel.HEAD_TRACKING) || has(Melody.SPATIAL_TYPE)) return false
        return !m.heyMelody || has(Melody.BUDS_SPATIAL)
    }

    private fun sound(wind: Boolean?): Sound? {
        val tree = noise ?: return null
        val reading = noiseRaw?.let { tree.read(it) } ?: return null
        reading.level?.let { lastLevel = it }
        val level = reading.level ?: lastLevel
        val cycle = cycleMask?.takeIf { model?.holdMask == 0 }?.let { mask -> tree.cycleBits().filterValues { mask shr it and 1 == 1 }.keys }
        return Sound(
            mode = reading.mode,
            cancelStyle = if (reading.mode == ListeningMode.NoiseCancelling && reading.style == CancelStyle.Adaptive) CancelStyle.Adaptive
            else if (tree.levels.isNotEmpty()) CancelStyle.Manual else tree.styles.firstOrNull() ?: CancelStyle.Manual,
            manualLevel = level?.let { tree.stepOf(it) } ?: 5,
            windReduction = wind == true,
            cycle = cycle ?: ListeningMode.entries.toSet(),
            options = SoundOptions(
                cancelStyles = tree.styles,
                transparencyStyles = false,
                wind = wind != null,
                environment = false,
                cycle = cycle != null,
                modes = tree.listening,
            ),
        )
    }

    private fun builtIns(): List<MelodyModel.Preset> {
        val m = model ?: return emptyList()
        if (!has(Melody.SET_EQ)) return emptyList()
        val version = Melody.firmwareNumber(firmware)
        return m.presets.filter { it.minFirmware <= version }.distinctBy { it.id }
    }

    private fun customAllowed() = model?.has(MelodyModel.CUSTOM_EQ) == true && has(Melody.SAVE_EQ) && customs != null

    /** The curve the custom sound shows and changes: the one playing, else the app's own, else the one last picked. */
    private fun shownCurve(): Melody.CustomEq? {
        val list = customs ?: return null
        return list.firstOrNull { it.id == eqId } ?: list.firstOrNull { it.name == OWN_CURVE } ?: list.firstOrNull { it.selected }
    }

    private fun equalizer(): Equalizer? {
        val presets = builtIns().map { EqPreset(it.id, it.name, emptyList()) }
        if (!customAllowed()) {
            if (presets.isEmpty()) return null
            return Equalizer(eqId ?: presets.first().id, presets, emptyList(), emptyList(), custom = false)
        }
        val curve = shownCurve()
        val playing = customs?.any { it.id == eqId } == true
        val frequencies = curve?.frequencies ?: model?.bands ?: DEFAULT_BANDS
        return Equalizer(
            presetId = if (playing || presets.isEmpty()) Equalizer.CUSTOM else eqId ?: presets.first().id,
            presets = presets,
            frequencies = frequencies,
            left = curve?.gains?.map { it.toDouble() } ?: List(frequencies.size) { 0.0 },
        )
    }

    /** The model's touch rows as presses, each with its choices. */
    private fun touchChoices(): Map<Press, Pair<Int, List<TouchAction>>> {
        val m = model ?: return emptyMap()
        if (m.has(MelodyModel.ONE_BUTTON)) return emptyMap()
        val rows = buildMap {
            for ((row, options) in m.touchRows) {
                val press = when (row) {
                    1, 16 -> Press.Single
                    2, 17 -> Press.Double
                    3, 18 -> Press.Triple
                    4 -> Press.Long
                    else -> null
                } ?: continue
                val choices = TAP_CHOICES.filter { (bit, _) -> options and bit != 0 }.map { it.second }
                if (choices.isNotEmpty()) put(press, press.touch to choices)
            }
            // Each earbud's own hold: nothing, the noise cycle or the assistant.
            if (m.holdMask != 0) {
                val choices = HOLD_CHOICES.filter { (bit, _) -> m.holdMask and bit != 0 }.map { it.second }
                if (choices.isNotEmpty()) put(Press.Long, 4 to choices)
            }
        }
        return rows
    }

    private fun controls(): TouchControls? {
        val table = taps ?: return null
        if (!has(Melody.SET_TAPS)) return null
        val rows = touchChoices()
        val actions = buildMap {
            for ((press, row) in rows) for (ear in Ear.entries) {
                val entry = table.firstOrNull { it.side == ear.side && it.touch == row.first && it.button != ON_CALL } ?: continue
                val action = action(entry.function) ?: continue
                if (action in row.second) put(ear to press, action)
            }
        }
        if (actions.isEmpty()) return null
        return TouchControls(
            actions = actions,
            available = rows.values.flatMap { it.second }.distinct(),
            availableFor = rows.mapValues { it.value.second },
        )
    }

    private val Ear.side get() = if (this == Ear.Left) 1 else 2

    private val Press.touch get() = when (this) {
        Press.Single -> 1
        Press.Double -> 2
        Press.Triple -> 3
        Press.Long -> 4
    }

    private fun oldTrack() = model?.has(MelodyModel.OLD_TRACK) == true

    private fun action(function: Int): TouchAction? = when (function) {
        0x00 -> TouchAction.None
        0x01 -> TouchAction.PlayPause
        0x03 -> TouchAction.VoiceAssistant
        0x08 -> TouchAction.ListeningMode
        0x0B -> TouchAction.VolumeUp
        0x0C -> TouchAction.VolumeDown
        0x04 -> if (oldTrack()) TouchAction.PreviousSong else null
        0x05 -> if (oldTrack()) TouchAction.NextSong else TouchAction.PreviousSong
        0x06 -> if (oldTrack()) null else TouchAction.NextSong
        else -> null
    }

    private fun function(action: TouchAction): Int? = when (action) {
        TouchAction.None -> 0x00
        TouchAction.PlayPause -> 0x01
        TouchAction.VoiceAssistant -> 0x03
        TouchAction.ListeningMode -> 0x08
        TouchAction.VolumeUp -> 0x0B
        TouchAction.VolumeDown -> 0x0C
        TouchAction.PreviousSong -> if (oldTrack()) 0x04 else 0x05
        TouchAction.NextSong -> if (oldTrack()) 0x05 else 0x06
    }

    override suspend fun refresh() {
        if (connection == null) {
            flow.update { it.copy(link = Link.Connecting) }
            wake.trySend(Unit)
            return
        }
        request(Melody.BATTERY)
    }

    override suspend fun apply(change: Change) {
        connection ?: return
        when (change) {
            is Change.Mode -> setNoise(noise?.bitFor(change.mode, lastLevel))
            is Change.Cancelling -> setNoise(noise?.bitFor(change.style, change.level, lastLevel))
            is Change.ModeCycle -> {
                val bits = noise?.cycleBits()?.takeIf { it.isNotEmpty() } ?: return
                if (change.modes.size < 2 || model?.holdMask != 0) return
                val known = bits.values.fold(0) { acc, b -> acc or (1 shl b) }
                val chosen = change.modes.mapNotNull { bits[it] }.fold(0) { acc, b -> acc or (1 shl b) }
                val mask = ((cycleMask ?: 0) and known.inv()) or chosen
                cycleMask = mask
                publish()
                request(Melody.SET_NOISE, Melody.setCycle(mask))
                delay(400)
                request(Melody.NOISE, byteArrayOf(2, 1))
            }
            is Change.Wind -> setSwitch(Melody.WIND, change.on)
            is Change.Switch -> when (change.toggle) {
                Toggle.WearDetection -> setSwitch(Melody.WEAR, change.on)
                Toggle.GamingMode -> setSwitch(Melody.GAME, change.on)
                Toggle.Surround -> setSwitch(Melody.SPATIAL, change.on)
                Toggle.DynamicBass -> setSwitch(Melody.BASS, change.on)
                else -> {}
            }
            is Change.TwoDevices -> {
                setSwitch(Melody.DUAL, change.on)
                // Then the phone's state, as the earbuds' own app sends it: automatic switching on after "off".
                if (has(Melody.PHONE_STATE)) request(Melody.PHONE_STATE, byteArrayOf(8, 0, if (change.on) 0 else 1))
            }
            is Change.Preset -> if (change.id == Equalizer.CUSTOM) {
                shownCurve()?.let { saveCurve(it) }
            } else if (builtIns().any { it.id == change.id }) {
                eqId = change.id
                publish()
                request(Melody.SET_EQ, byteArrayOf(change.id.toByte()))
                delay(300)
                request(Melody.EQ)
            }
            is Change.Bands -> setBands(change.left)
            is Change.Touch -> setTouch(change.ear, change.press, change.action)
            else -> {}
        }
    }

    private suspend fun setNoise(bit: Int?) {
        if (bit == null || noise == null) return
        noiseRaw = 1 shl bit
        publish()
        val answer = request(Melody.SET_NOISE, Melody.setNoise(bit))
        // 0E: no earbud is in an ear, so the earbuds keep the mode they have.
        if (answer != null && answer.at(0) != 0) log("noise control refused: ${answer.at(0)}")
        delay(400)
        request(Melody.NOISE, byteArrayOf(1, 1))
    }

    private suspend fun setSwitch(id: Int, on: Boolean) {
        if (!has(Melody.SET_SWITCH) || id !in switches) return
        switches[id] = if (on) 1 else 0
        publish()
        request(Melody.SET_SWITCH, byteArrayOf(id.toByte(), if (on) 1 else 0))
        // Game mode takes about a second to apply; a read sooner gives the old value.
        delay(if (id == Melody.GAME) 1_200 else 400)
        request(Melody.SWITCHES, askList(listOf(id)))
    }

    private suspend fun setTouch(ear: Ear, press: Press, action: TouchAction) {
        val table = taps ?: return
        val row = touchChoices()[press] ?: return
        if (action !in row.second) return
        val code = function(action) ?: return
        var matched = false
        // Every slot this earbud has for the touch, but not the call group.
        val updated = table.map {
            if (it.side == ear.side && it.touch == row.first && it.button != ON_CALL) {
                matched = true
                it.copy(function = code)
            } else it
        }
        if (!matched) return
        taps = updated
        publish()
        request(Melody.SET_TAPS, Melody.setTaps(updated))
        delay(400)
        request(Melody.TAPS)
    }

    /** Shapes the earbuds' custom curve: the one playing, else the app's own, made if there is room. */
    private suspend fun setBands(gains: List<Double>) {
        if (!customAllowed()) return
        val list = customs ?: return
        var curve = list.firstOrNull { it.id == eqId } ?: list.firstOrNull { it.name == OWN_CURVE }
        if (curve == null && list.size < CURVES) {
            val bands = model?.bands ?: DEFAULT_BANDS
            val made = Melody.CustomEq(0, OWN_CURVE, bands, List(bands.size) { 0 }, false)
            val answer = request(Melody.SAVE_EQ, Melody.saveEq(Melody.EQ_CREATE, made))
            if (answer == null || answer.at(0) != 0 || answer.payload.size < 2) return
            curve = made.copy(id = answer.at(1))
        }
        curve = curve ?: list.firstOrNull { it.selected } ?: list.firstOrNull() ?: return
        saveCurve(curve.copy(gains = fit(gains, curve.frequencies.size)))
    }

    /** Saves a curve and makes it the one playing, then reads the list and the choice back. */
    private suspend fun saveCurve(curve: Melody.CustomEq) {
        eqId = curve.id
        customs = customs?.map { if (it.id == curve.id) curve else it }
        publish()
        request(Melody.SAVE_EQ, Melody.saveEq(Melody.EQ_SAVE, curve))
        delay(300)
        request(Melody.CUSTOM_EQS)
        request(Melody.EQ)
    }

    /** Gains for [size] bands, in whole decibels from -6 to 6; a curve with another number of bands is spread over them. */
    private fun fit(gains: List<Double>, size: Int): List<Int> = List(size) { i ->
        val at = if (size <= 1 || gains.size <= 1) 0 else (i * (gains.size - 1) + (size - 1) / 2) / (size - 1)
        (gains.getOrElse(at) { 0.0 }).roundToInt().coerceIn(-6, 6)
    }

    override suspend fun ring(left: Boolean, right: Boolean) {
        if (!flow.value.canRing || connection == null) return
        // One sound from both earbuds; there is no choosing a side.
        request(Melody.FIND, byteArrayOf(if (left || right) 1 else 0))
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
        /** The name of the curve the app makes on the earbuds when they have room for one. */
        const val OWN_CURVE = "Blazbuds"

        /** How many custom curves the earbuds keep, as their own app allows. */
        private const val CURVES = 3

        private const val ON_CALL = 0x06
        private const val CUSTOM_EQ_NEWS = 0x0506
        private val DEFAULT_BANDS = listOf(62, 250, 1000, 4000, 8000, 16000)
        private val LATE_GAME = setOf(0x062410, 0x062810, 0x063010, 0x063410)

        /** Touch choices by their bit in the model list, in the earbuds' own app's order. */
        private val TAP_CHOICES = listOf(
            512 to TouchAction.None,
            4 to TouchAction.PlayPause,
            32 to TouchAction.PreviousSong,
            64 to TouchAction.NextSong,
            1 to TouchAction.VoiceAssistant,
            8 to TouchAction.VolumeUp,
            16 to TouchAction.VolumeDown,
        )
        private val HOLD_CHOICES = listOf(512 to TouchAction.None, 128 to TouchAction.ListeningMode, 1 to TouchAction.VoiceAssistant)
    }
}

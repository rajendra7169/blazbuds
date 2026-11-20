package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.model.CancelStyle
import com.blazify.buds.core.model.Ear
import com.blazify.buds.core.model.Equalizer
import com.blazify.buds.core.model.ListeningMode
import com.blazify.buds.core.model.Sound
import com.blazify.buds.core.model.SoundOptions
import com.blazify.buds.core.model.TouchAction
import com.blazify.buds.core.model.TransparencyStyle
import com.blazify.buds.core.model.Travel
import com.blazify.buds.core.soundcore.Packet
import com.blazify.buds.core.soundcore.generic.ActionKind
import com.blazify.buds.core.soundcore.generic.Actions
import com.blazify.buds.core.soundcore.generic.ButtonLayout
import com.blazify.buds.core.soundcore.generic.DisableMode
import com.blazify.buds.core.soundcore.generic.EnabledKind
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqState
import com.blazify.buds.core.soundcore.generic.HearIdState
import com.blazify.buds.core.soundcore.generic.Parsed
import com.blazify.buds.core.soundcore.generic.PressKind
import com.blazify.buds.core.soundcore.generic.PressSetup
import com.blazify.buds.core.soundcore.generic.Slot
import com.blazify.buds.core.soundcore.generic.SoundMigration
import com.blazify.buds.core.soundcore.generic.SoundMigration.Requirement
import com.blazify.buds.core.soundcore.generic.SoundScheme
import com.blazify.buds.core.soundcore.generic.Step
import com.blazify.buds.core.soundcore.generic.byteOf
import com.blazify.buds.core.soundcore.generic.modeOf
import com.blazify.buds.core.soundcore.generic.transparencyOf

/**
 * What the newer Soundcore earbuds (Space A40, the Liberty 3 Pro to Liberty 5, the P-series and Sport X20) share: their
 * listening-mode schemes, their touch layouts and their own ways of sending the equalizer.
 */
internal object NewerParts {
    /** The actions Soundcore's app offers for a single tap on some models: no mode switching and no voice assistant. */
    val MINIMAL_ACTIONS = Actions.COMMON.filter { it.first != 4 && it.first != 5 }

    /** Single, double, triple and long presses on each side, one byte each, the way the P40i and later models keep them. */
    fun fourTouches(single: List<Pair<Int, TouchAction>> = Actions.COMMON, ignoreEnabled: Boolean = false) = ButtonLayout(
        order = listOf(
            Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
            Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
            Slot(Ear.Left, PressKind.Triple), Slot(Ear.Right, PressKind.Triple),
            Slot(Ear.Left, PressKind.Long), Slot(Ear.Right, PressKind.Long),
        ),
        presses = listOf(
            PressSetup(PressKind.Single, 2, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, single),
            PressSetup(PressKind.Double, 0, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
            PressSetup(PressKind.Triple, 5, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
            PressSetup(PressKind.Long, 1, EnabledKind.None, ActionKind.TwsLowBits, DisableMode.EachSide, Actions.COMMON),
        ),
        setAll = false,
        ignoreEnabled = ignoreEnabled,
    )

    /** Single, double and long presses on each side, in that order. */
    val SINGLE_DOUBLE_LONG = listOf(
        Slot(Ear.Left, PressKind.Single), Slot(Ear.Right, PressKind.Single),
        Slot(Ear.Left, PressKind.Double), Slot(Ear.Right, PressKind.Double),
        Slot(Ear.Left, PressKind.Long), Slot(Ear.Right, PressKind.Long),
    )

    // Value checks the way OpenSCQ30 reads each field: an unknown value becomes the first one.
    fun oneOf(byte: Int, vararg allowed: Int) = if (byte in allowed) byte else allowed.first()

    fun mode(byte: Int) = oneOf(byte, 0, 1, 2)

    fun wind(byte: Int) = byte and 0x03

    /**
     * A listening-mode scheme read field by field, as OpenSCQ30's structure for the model does: [fields] reads the
     * bytes into the structure's fields (in the structure's own order), [bytes] writes them back. When the model needs
     * the Soundcore app's order of changes, [migration] holds which field may change only while another has which values.
     */
    abstract class FieldSound(override val size: Int, private val migration: SoundMigration? = null) : SoundScheme {
        abstract fun fields(bytes: IntArray): IntArray

        abstract fun bytes(fields: IntArray): IntArray

        /** The sound in the app's terms. */
        abstract fun show(f: IntArray): Sound

        /** Changes [f] to give [wanted], leaving what the app doesn't show as it was. */
        abstract fun change(f: IntArray, wanted: Sound)

        override fun read(bytes: IntArray): Sound = show(fields(bytes))

        override fun target(now: IntArray, wanted: Sound): IntArray = bytes(fields(now).also { change(it, wanted) })

        override fun steps(now: IntArray, target: IntArray): List<IntArray> {
            if (bytes(fields(now)).contentEquals(target)) return emptyList()
            val plan = migration ?: return listOf(target)
            return plan.path(fields(now).toList(), fields(target).toList()).map { bytes(it.toIntArray()) }
        }
    }

    /** What the third way of cancelling is on a model, besides manual (0) and adaptive (1). */
    enum class Third {
        None,

        /** Transport (0), outdoor (1) or indoor (2). */
        Scenes,

        /** Plane (0) or car (3). */
        Travel,
    }

    /** Where each field the app uses sits in a model's [FieldSound] structure; -1 for one the model doesn't have. */
    class Places(
        val mode: Int,
        val transparency: Int = -1,
        val cancelling: Int,
        val manual: Int,
        val third: Int = -1,
        val wind: Int,
        val environment: Int = -1,
    )

    /**
     * The most common newer scheme: the mode, a kind of cancelling (manual 0, adaptive 1, and on some models a third:
     * scenes or travel), a manual strength, optionally the kind of transparency, wind reduction and real-time adaptive
     * cancelling.
     */
    class AncSound(
        size: Int,
        private val at: Places,
        /** The kinds of cancelling the app offers, in the model's order. */
        private val styles: List<CancelStyle>,
        private val third: Third,
        private val manualLevels: Int,
        private val decode: (IntArray) -> IntArray,
        private val encode: (IntArray) -> IntArray,
        migration: SoundMigration? = null,
    ) : FieldSound(size, migration) {
        override fun fields(bytes: IntArray) = decode(bytes)

        override fun bytes(fields: IntArray) = encode(fields)

        override fun options(cycle: Boolean) = SoundOptions(
            cancelStyles = styles,
            transparencyStyles = at.transparency >= 0,
            wind = true,
            environment = at.environment >= 0,
            cycle = cycle,
            modes = listOf(ListeningMode.NoiseCancelling, ListeningMode.Transparency, ListeningMode.Normal),
            manualLevels = manualLevels,
        )

        override fun show(f: IntArray): Sound {
            val thirdByte = if (at.third >= 0) f[at.third] else 0
            val style = when (f[at.cancelling]) {
                0 -> CancelStyle.Manual
                1 -> CancelStyle.Adaptive
                else -> when (third) {
                    Third.Travel -> CancelStyle.Travel
                    else -> SCENES.getOrElse(thirdByte) { CancelStyle.Transport }
                }
            }
            return Sound(
                mode = modeOf(f[at.mode]),
                cancelStyle = style,
                manualLevel = f[at.manual].coerceIn(1, manualLevels),
                travel = if (third == Third.Travel && thirdByte == 3) Travel.Car else Travel.Plane,
                transparencyStyle = if (at.transparency >= 0) transparencyOf(f[at.transparency]) else TransparencyStyle.Full,
                windReduction = f[at.wind] and 1 != 0,
                environmentDetection = at.environment >= 0 && f[at.environment] == 1,
                options = options(false),
            )
        }

        override fun change(f: IntArray, wanted: Sound) {
            f[at.mode] = byteOf(wanted.mode)
            if (wanted.cancelStyle in styles) when (wanted.cancelStyle) {
                CancelStyle.Manual -> {
                    f[at.cancelling] = 0
                    f[at.manual] = wanted.manualLevel.coerceIn(1, manualLevels)
                }
                CancelStyle.Adaptive -> f[at.cancelling] = 1
                CancelStyle.Transport, CancelStyle.Outdoor, CancelStyle.Indoor -> {
                    f[at.cancelling] = 2
                    f[at.third] = SCENES.indexOf(wanted.cancelStyle)
                }
                // Only a plane and a car; the app's train and bus have no place on these earbuds.
                CancelStyle.Travel -> when (wanted.travel) {
                    Travel.Plane -> { f[at.cancelling] = 2; f[at.third] = 0 }
                    Travel.Car -> { f[at.cancelling] = 2; f[at.third] = 3 }
                    else -> {}
                }
            }
            if (at.transparency >= 0) f[at.transparency] = byteOf(wanted.transparencyStyle)
            f[at.wind] = (f[at.wind] and 0x02) or (if (wanted.windReduction) 1 else 0)
            if (at.environment >= 0) f[at.environment] = if (wanted.environmentDetection) 1 else 0
        }

        companion object {
            /** The scenes in the earbuds' order. */
            val SCENES = listOf(CancelStyle.Transport, CancelStyle.Outdoor, CancelStyle.Indoor)
        }
    }

    /**
     * The Space A40's scheme, shared by the Liberty 3 Pro (which needs the app's order of changes) and the Sport X20:
     * mode, manual (weak, moderate, strong) and adaptive strengths in one byte, kind of transparency, kind of
     * cancelling, wind, then a last byte (the adaptive sensitivity, an unknown byte, or always 0xFF on the Sport X20).
     * Fields in the Liberty 3 Pro's order: mode, transparency, kind, manual, adaptive, wind, last.
     */
    fun spaceA40Sound(migrate: Boolean, lastByte: Int? = null) = AncSound(
        size = 6,
        at = Places(mode = 0, transparency = 1, cancelling = 2, manual = 3, wind = 5),
        styles = listOf(CancelStyle.Manual, CancelStyle.Adaptive),
        third = Third.None,
        manualLevels = 3,
        decode = { b -> intArrayOf(mode(b[0]), oneOf(b[2], 0, 1), oneOf(b[3], 0, 1), oneOf(b[1] shr 4, 1, 2, 3), oneOf(b[1] and 0x0F, 0, 1, 2), wind(b[4]), b[5]) },
        encode = { f -> intArrayOf(f[0], (f[3] shl 4) or f[4], f[1], f[2], f[5], lastByte ?: f[6]) },
        migration = if (migrate) SoundMigration(
            listOf(null, Requirement(0, listOf(1)), Requirement(0, listOf(0)), Requirement(2, listOf(0)), Requirement(2, listOf(1)), Requirement(0, listOf(0, 1)), null),
        ) else null,
    )

    /** The P40i's and Liberty 5's migration: mode, transparency, kind, adaptive, manual, the third kind's choice, wind, sensitivity. */
    val P40I_MIGRATION = SoundMigration(
        listOf(
            null,
            Requirement(0, listOf(1)),
            Requirement(0, listOf(0)),
            Requirement(2, listOf(1)),
            Requirement(2, listOf(0)),
            Requirement(2, listOf(2)),
            Requirement(0, listOf(0, 1)),
            Requirement(2, listOf(1)),
        ),
    )

    // ---- Equalizers ----

    private fun bandBytes(values: IntArray?, count: Int, lastWhenMissing: Int = 0xFF): List<Int> =
        if (values == null) List(count) { if (it == count - 1) lastWhenMissing else 0xFF }
        else List(count) { (values.getOrElse(it) { 0 }.coerceIn(-120, 134) + 120) }

    private fun target(p: Parsed, presetId: Int, tenths: IntArray): EqState? {
        val eq = p.eq ?: return null
        val id = if (presetId == Equalizer.CUSTOM) EqSetup.CUSTOM_ID else presetId
        return EqState(id, eq.channels.map { tenths.copyOf() })
    }

    private fun send(target: EqState, body: List<Int>): List<Step> = listOf(
        Step(Packet(EqSetup.SET_HEAR_ID, ByteArray(body.size) { body[it].toByte() })) {
            it.eq = target
            it.hearId?.enabled = false
        },
    )

    /** An empty hearing profile, as the P40i reads one it was told isn't there. */
    private val BLANK = HearIdState(false, listOf(null, null), 0, 0, 0, listOf(null, null))

    /**
     * The P40i's and Liberty 4's equalizer message (03 87): the bands with the hearing profile switched off, whether the
     * profile was ever made, and the limiter's curve. [made] says from the state whether a profile was made; the P40i
     * also forgets the profile it sent when it says there is none ([blankWhenNotMade]).
     */
    fun p40iEqualizer(made: (Parsed) -> Boolean, blankWhenNotMade: Boolean): (Parsed, Int, IntArray) -> List<Step> = { p, presetId, tenths ->
        target(p, presetId, tenths)?.let { eq ->
            val isMade = made(p)
            val hear = if (blankWhenNotMade && !isMade) BLANK else p.hearId ?: BLANK
            val bands = tenths.size
            val body = buildList {
                add(eq.presetId and 0xFF); add(eq.presetId shr 8 and 0xFF)
                add(hear.genre and 0xFF); add(hear.genre shr 8 and 0xFF)
                eq.channels.forEach { addAll(bandBytes(it, bands)) }
                if (isMade) addAll(listOf(0, 0)) else addAll(listOf(0xFF, 0xFF))
                add(0) // the hearing profile, switched off
                hear.curves.forEach { addAll(bandBytes(it, bands, lastWhenMissing = 0)) }
                addAll(time(hear.time))
                add(hear.type)
                hear.customCurves.forEach { addAll(bandBytes(it, bands, lastWhenMissing = 0)) }
                eq.channels.forEach { addAll(bandBytes(it?.let { values -> EqSetup.drc(values) }, bands)) }
                add(0)
            }
            send(eq, body)
        } ?: emptyList()
    }

    /**
     * The Liberty 4 Pro's, Liberty 5's and P31i's equalizer message (03 87): the bands, the hearing profile switched off,
     * then the bands that play, each followed by a zero.
     */
    fun liberty4ProEqualizer(): (Parsed, Int, IntArray) -> List<Step> = { p, presetId, tenths ->
        target(p, presetId, tenths)?.let { eq ->
            val hear = p.hearId ?: BLANK
            val bands = tenths.size
            val playing = eq.channels.flatMap { bandBytes(it, bands) }
            val body = buildList {
                add(eq.presetId and 0xFF); add(eq.presetId shr 8 and 0xFF)
                add(hear.genre and 0xFF); add(hear.genre shr 8 and 0xFF)
                addAll(playing)
                addAll(listOf(0, 0))
                add(0) // the hearing profile, switched off
                hear.curves.forEach { addAll(bandBytes(it, bands)) }
                addAll(time(hear.time))
                add(hear.type)
                hear.customCurves.forEach { addAll(bandBytes(it, bands)) }
                playing.forEach { add(it); add(0) }
                addAll(listOf(0, 0))
            }
            send(eq, body)
        } ?: emptyList()
    }

    private fun time(t: Long) = listOf((t shr 24).toInt() and 0xFF, (t shr 16).toInt() and 0xFF, (t shr 8).toInt() and 0xFF, t.toInt() and 0xFF)
}

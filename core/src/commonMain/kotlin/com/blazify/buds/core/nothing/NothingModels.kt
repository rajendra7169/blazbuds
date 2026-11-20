package com.blazify.buds.core.nothing

import com.blazify.buds.core.model.Press

/** What shape a Nothing or CMF product is: two earbuds and a case, or one piece. */
enum class NothingForm { Earbuds, Headphones, Neckband }

/** Which equalizer presets a model has, and the command that picks them. */
enum class NothingEq {
    /** Balanced, voice, more treble, more bass; picked with F010, read with C01F. Custom is 5. */
    Nothing,

    /** The CMF models' Dirac Opteo sounds; picked with F01D, read with C050. Custom is 6. */
    Dirac,
}

/** The three filters of a model's own sound (bass, mid, treble): where each sits and how wide it is. */
data class NothingCurve(val lowHz: Float, val lowQ: Float, val midHz: Float, val midQ: Float, val highHz: Float, val highQ: Float)

/**
 * One Nothing or CMF model, by the product number it reports (read C01C), with what Nothing's own app offers on it.
 * [noise] holds the noise modes as the earbuds number them; [taps] what each earbud's double, triple and long press
 * can be set to, as Nothing's action numbers.
 */
data class NothingModel(
    val id: String,
    val name: String,
    val form: NothingForm = NothingForm.Earbuds,
    val noise: Set<Int> = ALL_NOISE,
    val eq: NothingEq = NothingEq.Nothing,
    val curve: NothingCurve? = null,
    /**
     * Nothing's eight-band "Advanced" sound. The models are the ones at least two of ear (web), BudsLink and
     * swift-nothing-ear give it; the earbuds must still answer for it before it shows.
     */
    val advanced: Boolean = false,
    /** The ear tip fit test: the models ear (web) runs it on, and the second CMF Buds Pro 2 from BudsLink. */
    val fitTest: Boolean = false,
    /** Personalised noise cancelling: the Ear (2) in ear (web) and BudsLink, the Ear and Ear (3) in BudsLink. */
    val personal: Boolean = false,
    val bass: Boolean = false,
    val spatial: Boolean = false,
    val inEar: Boolean = true,
    val taps: Map<Press, List<Int>> = emptyMap(),
) {
    val adaptive: Boolean get() = NothingNoise.ADAPTIVE in noise

    /** The cancelling strengths, lightest first. */
    val strengths: List<Int> get() = listOf(NothingNoise.LOW, NothingNoise.MID, NothingNoise.HIGH).filter { it in noise }

    companion object {
        val ALL_NOISE = setOf(NothingNoise.HIGH, NothingNoise.MID, NothingNoise.LOW, NothingNoise.ADAPTIVE, NothingNoise.OFF, NothingNoise.TRANSPARENCY)

        private val EAR = NothingCurve(140f, 0.8f, 980f, 0.7f, 3400f, 1.0f)
        private val CMF = NothingCurve(140f, 0.8f, 980f, 0.7f, 6900f, 1.0f)
        private val WIDE = NothingCurve(140f, 0.8f, 980f, 0.7f, 3500f, 1.0f)

        private const val N = NothingAction.NONE
        private const val PP = NothingAction.PLAY_PAUSE
        private const val PR = NothingAction.PREVIOUS
        private const val NX = NothingAction.NEXT
        private const val NC = NothingAction.NOISE
        private const val VA = NothingAction.VOICE
        private const val UP = NothingAction.VOLUME_UP
        private const val DN = NothingAction.VOLUME_DOWN

        private val EAR_TAPS = mapOf(Press.Double to listOf(PR, NX, VA), Press.Triple to listOf(PR, NX, VA), Press.Long to listOf(NC, UP, DN, VA))
        private val OPEN_TAPS = mapOf(Press.Double to listOf(PR, NX, VA), Press.Triple to listOf(PR, NX, VA), Press.Long to listOf(UP, DN, VA))
        private val CMF_TAPS = mapOf(Press.Double to listOf(PP, PR, NX, VA, N), Press.Triple to listOf(PR, NX, VA, N), Press.Long to listOf(NC, VA, N))

        private val CANCEL_HIGH = setOf(NothingNoise.HIGH, NothingNoise.OFF, NothingNoise.TRANSPARENCY)

        /**
         * The models Nothing's app knows, from its own per-model feature table as PairFree, ear (web) and Gadgetbridge
         * read it. The headphones and the neckband are one piece with buttons of their own, so their controls are left
         * out; so are the Ear (3a)'s, which took new actions without using them in nadamas's test.
         */
        val ALL: List<NothingModel> = listOf(
            NothingModel("B181", "Nothing Ear (1)", noise = setOf(NothingNoise.HIGH, NothingNoise.LOW, NothingNoise.OFF, NothingNoise.TRANSPARENCY), taps = mapOf(Press.Triple to listOf(PR, NX, VA, N), Press.Long to listOf(NC, N))),
            NothingModel("B155", "Nothing Ear (2)", curve = NothingCurve(140f, 0.8f, 980f, 0.7f, 3400f, 0.7f), advanced = true, fitTest = true, personal = true, taps = EAR_TAPS),
            NothingModel("B157", "Nothing Ear (stick)", noise = setOf(NothingNoise.HIGH, NothingNoise.OFF), curve = NothingCurve(140f, 0.8f, 980f, 0.66f, 3500f, 1.0f), advanced = true, taps = OPEN_TAPS),
            NothingModel("B162", "Nothing Ear (a)", curve = EAR, fitTest = true, bass = true, taps = EAR_TAPS),
            NothingModel("B171", "Nothing Ear", curve = EAR, advanced = true, fitTest = true, personal = true, bass = true, taps = EAR_TAPS),
            NothingModel(
                "B173", "Nothing Ear (3)", curve = EAR, advanced = true, fitTest = true, personal = true, bass = true, spatial = true,
                taps = mapOf(Press.Double to listOf(NX, PR, VA), Press.Triple to listOf(NX, PR, VA, N), Press.Long to listOf(NC, VA, UP, DN, N)),
            ),
            NothingModel("B174", "Nothing Ear (open)", noise = emptySet(), curve = EAR, advanced = true, inEar = false, taps = OPEN_TAPS),
            // Its own three-band sound sits where the Ear's does (swift-nothing-ear, BudsLink).
            NothingModel("B190", "Nothing Ear (3a)", curve = EAR, advanced = true, fitTest = true, bass = true, spatial = true),
            NothingModel("B170", "Nothing Headphone (1)", NothingForm.Headphones, curve = WIDE, advanced = true, bass = true, spatial = true),
            NothingModel("B163", "CMF Buds Pro", noise = setOf(NothingNoise.HIGH, NothingNoise.MID, NothingNoise.LOW, NothingNoise.OFF, NothingNoise.TRANSPARENCY), curve = EAR, taps = mapOf(Press.Double to listOf(PP, PR, NX, VA), Press.Triple to listOf(PR, NX, VA), Press.Long to listOf(NC, VA))),
            NothingModel("B164", "CMF Neckband Pro", NothingForm.Neckband, curve = CMF, bass = true, spatial = true, inEar = false),
            NothingModel("B168", "CMF Buds", noise = CANCEL_HIGH, eq = NothingEq.Dirac, curve = CMF, bass = true, taps = CMF_TAPS),
            NothingModel("B172", "CMF Buds Pro 2", eq = NothingEq.Dirac, curve = CMF, fitTest = true, bass = true, spatial = true, taps = CMF_TAPS),
            NothingModel("B187", "CMF Buds Pro 2", eq = NothingEq.Dirac, curve = CMF, fitTest = true, bass = true, spatial = true, taps = CMF_TAPS),
            NothingModel("B179", "CMF Buds 2", eq = NothingEq.Dirac, curve = CMF, fitTest = true, bass = true, spatial = true, taps = CMF_TAPS),
            NothingModel("B184", "CMF Buds 2 Plus", eq = NothingEq.Dirac, curve = CMF, fitTest = true, bass = true, spatial = true, taps = CMF_TAPS),
            NothingModel("B185", "CMF Buds 2a", noise = CANCEL_HIGH, eq = NothingEq.Dirac, curve = CMF, bass = true, inEar = false, taps = CMF_TAPS),
            NothingModel("B175", "CMF Headphone Pro", NothingForm.Headphones, eq = NothingEq.Dirac, curve = WIDE, spatial = true, inEar = false),
        )

        fun byId(id: String): NothingModel? = ALL.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}

/** The mode numbers: high, mid and low cancelling, adaptive, off, transparency. */
object NothingNoise {
    const val HIGH = 1
    const val MID = 2
    const val LOW = 3
    const val ADAPTIVE = 4
    const val OFF = 5
    const val TRANSPARENCY = 7
}

/** Nothing's action numbers the app offers. */
object NothingAction {
    const val NONE = 1
    const val PLAY_PAUSE = 2
    const val PREVIOUS = 8
    const val NEXT = 9
    const val NOISE = 10
    const val VOICE = 11
    const val VOLUME_UP = 18
    const val VOLUME_DOWN = 19

    /** Noise cycles a press may already hold: shown as switching the mode; only [NOISE] is written. */
    val NOISE_CYCLES = setOf(10, 20, 21, 22)
}

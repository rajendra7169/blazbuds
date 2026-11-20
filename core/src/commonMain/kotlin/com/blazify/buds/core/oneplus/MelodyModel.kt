package com.blazify.buds.core.oneplus

/**
 * One model in [MELODY_MODELS]: its product id and name, the switch ids to ask it for, and its noise modes, presets
 * and touch choices in the short text form described there.
 */
class MelodyModel(
    val id: Int,
    val name: String,
    private val ask: String? = null,
    private val anc: String = "",
    private val eq: String = "",
    private val taps: String = "",
    /** Each earbud has its own hold, and these are its choices (512 nothing, 128 noise control, 1 assistant). */
    val holdMask: Int = 0,
    /** The bands of a new custom curve, where the model has its own. */
    val bands: List<Int>? = null,
    private val flags: Int = 0,
) {
    fun has(flag: Int): Boolean = flags and flag != 0

    /** OnePlus and OPPO models, from HeyMelody's list; realme ones come from realme Link's. */
    val heyMelody: Boolean get() = name.startsWith("OnePlus") || name.startsWith("OPPO")

    /** The switch ids to ask for: the model's own list, or HeyMelody's default, with game mode always. */
    val askIds: List<Int>
        get() {
            val base = ask?.split(' ')?.map { it.toInt(16) } ?: Melody.DEFAULT_ASK
            val extra = buildList {
                add(Melody.GAME)
                if (has(WIND)) add(Melody.WIND)
                if (has(BASS_BANDS)) add(Melody.BASS)
            }
            return (base + extra).distinct()
        }

    /** A noise mode: its kind (1 off, 2 transparency, 3 light, 8 medium, 4 strong, 5 cancelling, 7 smart, 10 adaptive) and bit. */
    data class Mode(val kind: Int, val bit: Int, val children: List<Mode> = emptyList())

    val modes: List<Mode> by lazy {
        Regex("""(\d+):(\d+)(?:\(([^)]*)\))?""").findAll(anc).map { m ->
            val children = m.groupValues[3].split(' ').filter { it.isNotBlank() }.map { child ->
                val (kind, bit) = child.split(':').map { it.toInt() }
                Mode(kind, bit)
            }
            Mode(m.groupValues[1].toInt(), m.groupValues[2].toInt(), children)
        }.toList()
    }

    data class Preset(val id: Int, val name: String, val minFirmware: Int)

    /** The built-in presets, in the order the earbuds' own app shows them. */
    val presets: List<Preset> by lazy {
        eq.split('|').filter { it.isNotBlank() }.map { entry ->
            val id = entry.substringBefore(':').toInt()
            val rest = entry.substringAfter(':')
            Preset(id, rest.substringBefore('@'), rest.substringAfter('@', "0").toInt())
        }
    }

    /** The touch rows: the model's row number and its choices as bits. */
    val touchRows: List<Pair<Int, Int>> by lazy {
        taps.split(' ').filter { it.isNotBlank() }.map { row ->
            val (gesture, options) = row.split(':').map { it.toInt() }
            gesture to options
        }
    }

    companion object {
        const val CUSTOM_EQ = 1
        const val WIND = 2
        /** OnePlus Buds and Buds Z: previous and next track are functions 4 and 5, not 5 and 6. */
        const val OLD_TRACK = 4
        /** The earbuds list the find command but play no sound. */
        const val NO_FIND = 8
        /** A neckband with one button. */
        const val ONE_BUTTON = 16
        /** realme: 3D sound is switch 1B alone. */
        const val SPATIAL_TOGGLE = 32
        const val SHARED_HOLD = 64
        const val HOLD_ANC_ONE = 128
        /** realme's dynamic sound, switched by 1D. */
        const val BASS_BANDS = 256
        /** 3D sound with head tracking, set through its own command, not the switch. */
        const val HEAD_TRACKING = 512

        /** The model with this Bluetooth name exactly, else the one with this product id; one matching both wins. */
        fun find(name: String?, productId: Int?): MelodyModel? {
            val byName = MELODY_MODELS.filter { name != null && it.name.equals(name.trim(), ignoreCase = true) }
            return byName.firstOrNull { it.id == productId } ?: byName.firstOrNull() ?: MELODY_MODELS.firstOrNull { it.id == productId }
        }

        /** Whether a Bluetooth name is one of the models in the list. */
        fun knows(name: String): Boolean = MELODY_MODELS.any { it.name.equals(name.trim(), ignoreCase = true) }
    }
}

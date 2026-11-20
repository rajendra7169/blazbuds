package com.blazify.buds.core.samsung

/**
 * Samsung's Galaxy Buds, each with what tells it apart in the protocol: the service it talks on, the way it frames
 * messages, where its noise control lives and which touch-and-hold actions it has.
 *
 * Samsung support is adapted from GalaxyBudsClient by Tim Schneeberger (github.com/timschneeb/GalaxyBudsClient,
 * GPL-3.0), with facts checked against Gadgetbridge's Galaxy Buds support (codeberg.org/Freeyourgadget/Gadgetbridge).
 * Only what those sources agree on, or what one of them documents plainly, is used here.
 */
enum class SamsungModel(
    /** The name to show. */
    val label: String,
    /** How the earbuds call themselves over Bluetooth, from the start of the name. */
    private val pattern: Regex,
    /** The RFCOMM service the control channel is on. */
    val service: String,
    /** The model number at the front of Samsung's firmware names. */
    val build: String,
) {
    Buds("Galaxy Buds", name("Galaxy Buds \\("), LEGACY, "R170"),
    BudsPlus("Galaxy Buds+", name("(Galaxy )?Buds\\+"), SERIAL, "R175"),
    BudsLive("Galaxy Buds Live", name("(Galaxy )?Buds Live"), SERIAL, "R180"),
    BudsPro("Galaxy Buds Pro", name("(Galaxy )?Buds Pro"), SERIAL, "R190"),
    Buds2Pro("Galaxy Buds2 Pro", name("(Galaxy )?Buds2 Pro"), NEW, "R510"),
    Buds2("Galaxy Buds2", name("(Galaxy )?Buds2"), NEW, "R177"),
    BudsFe("Galaxy Buds FE", name("(Galaxy )?Buds FE"), NEW, "R400N"),
    BudsCore("Galaxy Buds Core", name("(Galaxy )?Buds Core"), NEW, "R410"),
    Buds3Pro("Galaxy Buds3 Pro", name("(Galaxy )?Buds3 Pro"), NEW, "R630"),
    Buds3Fe("Galaxy Buds3 FE", name("(Galaxy )?Buds3 FE"), NEW, "R420"),
    Buds3("Galaxy Buds3", name("(Galaxy )?Buds3"), NEW, "R530"),
    Buds4Pro("Galaxy Buds4 Pro", name("(Galaxy )?Buds4 Pro"), NEW, "R640"),
    Buds4("Galaxy Buds4", name("(Galaxy )?Buds4"), NEW, "R540"),
    ;

    /** The 2019 Buds frame messages the old way: FE … EE with a type byte and a one-byte size. */
    val legacy: Boolean get() = this == Buds

    /** How the earbuds switch their noise control. */
    val noise: Noise
        get() = when (this) {
            Buds, BudsPlus -> Noise.Ambient
            BudsLive -> Noise.Cancelling
            else -> Noise.Controls
        }

    /** Adaptive noise control, which the earbuds steer themselves; documented for the Buds3 Pro only. */
    val adaptive: Boolean get() = this == Buds3Pro

    /**
     * The pinch-and-swipe generation (Buds3 and later). The sources disagree on how these lock their controls and which
     * actions their hold can take, so those parts are left out for them.
     */
    val pinch: Boolean get() = this == Buds3 || this == Buds3Pro || this == Buds3Fe || this == Buds4 || this == Buds4Pro

    /**
     * How many strengths manual noise cancelling has, 0 for one fixed strength: low and high on the Buds Pro, Buds FE,
     * Buds Core and Buds3 FE, as GalaxyBudsClient lists them, and five on the Buds3 Pro, as Gadgetbridge sets them.
     */
    val cancelLevels: Int
        get() = when (this) {
            BudsPro, BudsFe, BudsCore, Buds3Fe -> 2
            Buds3Pro -> 5
            else -> 0
        }

    /**
     * Voice detect: when you start talking, the earbuds let the world in and turn the music down, and go back a few
     * seconds after you stop. The Pro models only, as GalaxyBudsClient lists them.
     */
    val voiceDetect: Boolean get() = this == BudsPro || this == Buds2Pro || this == Buds3Pro || this == Buds4Pro

    /** Whether the earbuds say which of them, and the case, are charging, from the given extended status revision. */
    fun chargingFrom(revision: Int): Boolean = when (this) {
        Buds, BudsPlus, BudsLive, BudsPro -> false
        Buds2 -> revision >= 10
        Buds2Pro -> revision >= 11
        else -> true
    }

    /** Each touch-and-hold choice the earbuds have, by its number. */
    val holdCodes: Map<Hold, Int>
        get() = when (this) {
            Buds -> mapOf(Hold.Voice to 0, Hold.Volume to 2, Hold.Noise to 3)
            else -> if (pinch) mapOf(Hold.Voice to 1, Hold.Noise to 2) else mapOf(Hold.Voice to 1, Hold.Noise to 2, Hold.Volume to 3)
        }

    /** The touch lock: none, the one switch of the older ones, or the switch with each gesture beside it. */
    fun lock(revision: Int): Lock = when (this) {
        Buds, BudsPlus, BudsLive, BudsPro -> Lock.Simple
        Buds2 -> if (revision >= 4) Lock.Gestures else Lock.Simple
        Buds2Pro, BudsFe, BudsCore -> Lock.Gestures
        else -> Lock.None
    }

    /** Whether the gesture lock also carries the two settings for calls. */
    fun lockForCalls(revision: Int): Boolean = when (this) {
        Buds2 -> revision >= 7
        Buds2Pro -> revision >= 1
        else -> true
    }

    enum class Noise {
        /** Ambient sound on or off (2019 Buds, Buds+). */
        Ambient,

        /** Noise cancelling on or off (Buds Live). */
        Cancelling,

        /** One setting for off, cancelling, ambient and, on some, adaptive (Buds Pro and later). */
        Controls,
    }

    enum class Hold { Voice, Noise, Volume }

    enum class Lock { None, Simple, Gestures }

    companion object {
        /** The 2019 Buds' service; its odd last byte is how Samsung published it. */
        const val LEGACY_SERVICE = LEGACY

        /** The plain serial-port service of the Buds+, Buds Live and Buds Pro. */
        const val SERIAL_SERVICE = SERIAL

        /** Samsung's own service, from the Buds2 on. */
        const val NEW_SERVICE = NEW

        /** The model these earbuds' name points to, or null if it isn't a Galaxy Buds name. */
        fun of(name: String): SamsungModel? = entries.firstOrNull { it.pattern.containsMatchIn(name.trim()) }
    }
}

private fun name(start: String) = Regex("^$start", RegexOption.IGNORE_CASE)

private const val LEGACY = "00001102-0000-1000-8000-00805f9b34fd"
private const val SERIAL = "00001101-0000-1000-8000-00805f9b34fb"
private const val NEW = "2e73a4ad-332d-41fc-90e2-16bef06523f2"

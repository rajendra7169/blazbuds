package com.blazify.buds.core.catalog

import com.blazify.buds.core.link.Device

/** How much the app can do with a pair. */
enum class Support {
    /** Every setting: modes, sound, touch controls, devices. */
    Full,

    /** Battery for each earbud and the case, and the model's details, from the earbuds themselves. */
    Battery,

    /** What the phone or computer itself knows for any earbuds: battery and the connection. */
    Basic,
}

/** Which protocol a model speaks, for picking its driver. */
enum class Family { Soundcore, Huawei, Jieli, Samsung, Nothing, None }

/** What a device looks like, for its picture. */
enum class Kind { StemBuds, RoundBuds, Headphones, Speaker }

/** A model the app knows by name, with what it can do and how to put it in pairing mode. */
data class Model(
    val name: String,
    val support: Support,
    val pairing: List<String>,
    val family: Family = Family.None,
)

/**
 * Which earbuds the app recognises. New brands join here as their drivers are written; anything else still gets the
 * basics.
 */
object Catalog {
    private val LIBERTY_4_NC = Model(
        "Liberty 4 NC",
        Support.Full,
        listOf(
            "Put both earbuds in the case and leave the lid open.",
            "Press and hold the button on the case for 3 seconds, until the light flashes white.",
            "Pick \"soundcore Liberty 4 NC\" in your Bluetooth settings.",
        ),
        Family.Soundcore,
    )

    private val HONOR_PAIRING = listOf(
        "Put both earbuds in the case and leave the lid open.",
        "Press and hold the button on the case for 2 seconds, until the light flashes.",
        "Pick your earbuds in your Bluetooth settings, then come back here.",
    )

    private val GENERIC_PAIRING = listOf(
        "Put both earbuds in the case and open the lid.",
        "Hold the case's button, or touch and hold both earbuds, until the light flashes.",
        "Pick your earbuds in your Bluetooth settings, then come back here.",
    )

    fun model(device: Device): Model = when {
        device.name.contains("Liberty 4 NC", ignoreCase = true) -> LIBERTY_4_NC
        // Honor and Huawei earbuds share one protocol; Honor Choice ones call themselves "Earbuds X…".
        HUAWEI_NAMES.any { it.containsMatchIn(device.name) } -> Model(device.name, Support.Battery, HONOR_PAIRING, Family.Huawei)
        // Earbuds on a JieLi chip with their maker's app (checked: Ultima Boom Sleek).
        JIELI_NAMES.any { it.containsMatchIn(device.name) } -> Model(device.name, Support.Full, GENERIC_PAIRING, Family.Jieli)
        // Galaxy Buds+, Buds Live, Buds Pro, Buds2, Buds3, Buds FE and the rest, by the name Samsung gives them.
        com.blazify.buds.core.samsung.SamsungModel.of(device.name) != null -> Model(device.name, Support.Full, SAMSUNG_PAIRING, Family.Samsung)
        NOTHING_NAMES.any { it.containsMatchIn(device.name) } -> Model(device.name, Support.Full, GENERIC_PAIRING, Family.Nothing)
        else -> Model(device.name, Support.Basic, GENERIC_PAIRING)
    }

    private val SAMSUNG_PAIRING = listOf(
        "Put both earbuds in the case, close the lid for a few seconds, then open it.",
        "Pick your Galaxy Buds in your Bluetooth settings, then come back here.",
    )

    private val JIELI_NAMES = listOf(Regex("^Ultima ", RegexOption.IGNORE_CASE))

    /** Nothing and CMF earbuds and headphones; older Nothing ones call themselves just "Ear (2)" or "ear (1)". */
    private val NOTHING_NAMES = listOf(
        Regex("^Nothing (Ear|Headphone)", RegexOption.IGNORE_CASE),
        Regex("^Ear \\(", RegexOption.IGNORE_CASE),
        Regex("^CMF (Buds|Neckband|Headphone)", RegexOption.IGNORE_CASE),
    )

    private val HUAWEI_NAMES = listOf(
        Regex("^Earbuds X\\d", RegexOption.IGNORE_CASE),
        Regex("^HONOR (Earbuds|Choice|Magic)", RegexOption.IGNORE_CASE),
        Regex("^HUAWEI FreeBuds", RegexOption.IGNORE_CASE),
        Regex("^FreeBuds", RegexOption.IGNORE_CASE),
    )

    /** Guessed from the name, as Bluetooth doesn't say what shape a device is. */
    fun kind(name: String): Kind {
        val n = name.lowercase()
        return when {
            n.startsWith("ultima boom") || n.startsWith("earbuds x") -> Kind.StemBuds
            listOf("speaker", "boom", "flip", "charge", "motion", "soundlink", "partybox", "go ").any { it in n } -> Kind.Speaker
            listOf("wh-", "headphone", "space", "life q", "q20", "q30", "q35", "q45", "qc4", "qc45", "700", "max", "momentum 4", "rockerz", "tune 7").any { it in n } -> Kind.Headphones
            listOf("liberty", "airpods", "freebuds pro", "buds pro", "x7e", "nothing ear", "pixel buds pro").any { it in n } -> Kind.StemBuds
            else -> Kind.RoundBuds
        }
    }

    /** Pairing steps to show before anything is paired. */
    val pairingHelp: List<Pair<String, List<String>>> = listOf(
        "Soundcore Liberty 4 NC" to LIBERTY_4_NC.pairing,
        "Other earbuds" to GENERIC_PAIRING,
    )
}

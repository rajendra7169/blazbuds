package com.blazify.buds.core.catalog

import com.blazify.buds.core.link.Device

/** How much the app can do with a pair. */
enum class Support {
    /** Every setting: modes, sound, touch controls, devices. */
    Full,

    /** What the phone or computer itself knows for any earbuds: battery and the connection. */
    Basic,
}

/** A model the app knows by name, with what it can do and how to put it in pairing mode. */
data class Model(
    val name: String,
    val support: Support,
    val pairing: List<String>,
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
    )

    private val GENERIC_PAIRING = listOf(
        "Put both earbuds in the case and open the lid.",
        "Hold the case's button, or touch and hold both earbuds, until the light flashes.",
        "Pick your earbuds in your Bluetooth settings, then come back here.",
    )

    fun model(device: Device): Model = when {
        device.name.contains("Liberty 4 NC", ignoreCase = true) -> LIBERTY_4_NC
        else -> Model(device.name, Support.Basic, GENERIC_PAIRING)
    }

    /** Pairing steps to show before anything is paired. */
    val pairingHelp: List<Pair<String, List<String>>> = listOf(
        "Soundcore Liberty 4 NC" to LIBERTY_4_NC.pairing,
        "Other earbuds" to GENERIC_PAIRING,
    )
}

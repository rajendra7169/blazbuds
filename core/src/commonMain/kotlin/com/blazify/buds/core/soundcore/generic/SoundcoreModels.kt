package com.blazify.buds.core.soundcore.generic

import com.blazify.buds.core.soundcore.generic.models.A3951
import com.blazify.buds.core.soundcore.generic.models.CLASSIC_MODELS
import com.blazify.buds.core.soundcore.generic.models.EARLIER_MODELS
import com.blazify.buds.core.soundcore.generic.models.HEADPHONE_MODELS
import com.blazify.buds.core.soundcore.generic.models.NEWER_MODELS

/** Every Soundcore model the shared driver knows, and finding one by the name a device gives itself. */
object SoundcoreModels {
    val all: List<SoundcoreModel> = listOf(A3951) + EARLIER_MODELS + CLASSIC_MODELS + NEWER_MODELS + HEADPHONE_MODELS

    /** Each name a model goes by, lowercased, longest first so "Space One Pro" wins over "Space One". */
    private val names: List<Pair<String, SoundcoreModel>> =
        all.flatMap { model -> (listOf(model.name) + model.aliases).map { it.lowercase() to model } }.sortedByDescending { it.first.length }

    /**
     * The model a device is, from the name it gives over Bluetooth: "soundcore Life Q30", "Soundcore P40i", or the bare
     * name. The name must match whole, or be followed by a space (some add a side or a number). Null for anything else.
     */
    fun find(deviceName: String): SoundcoreModel? {
        val bare = deviceName.trim().lowercase().removePrefix("soundcore").trim()
        if (bare.isEmpty()) return null
        names.firstOrNull { it.first == bare }?.let { return it.second }
        return names.firstOrNull { bare.startsWith(it.first + " ") }?.second
    }

    fun byCode(code: String): SoundcoreModel? = all.firstOrNull { it.code.equals(code, ignoreCase = true) }
}

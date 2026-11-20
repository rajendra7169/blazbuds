package com.blazify.buds.core.soundcore.generic.models

import com.blazify.buds.core.soundcore.generic.SoundcoreModel

/**
 * Soundcore's headphones and the other models with a family of their own: the Space One, Space One Pro, Space Q45 and
 * Space 2 headphones, the V20i open earbuds and the Sleep A30 sleep earbuds.
 *
 * The Motion+ speaker (A3116) isn't here: it frames its messages without the closing checksum every other model
 * sends, which the shared driver can't do yet.
 */
val HEADPHONE_MODELS: List<SoundcoreModel> = listOf(A3035, A3062, A3040, D1402, A3876, D1301)

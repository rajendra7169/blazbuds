package com.blazify.buds.core.samsung

/**
 * What a pair of Galaxy Buds says about itself in its status messages. Batteries are whole percent and null when the
 * earbuds don't know (0, or above 100 for a closed case); a setting is null when this model doesn't send it.
 *
 * The positions are GalaxyBudsClient's (ExtendedStatusUpdateDecoder and StatusUpdateDecoder, Tim Schneeberger,
 * GPL-3.0), checked against its recorded messages from real earbuds.
 */
data class SamsungStatus(
    /** The version of this message's layout; later firmware adds fields at the end. */
    val revision: Int,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    /** Both earbuds are out and linked to each other. */
    val coupled: Boolean,
    /** Which of left, right and the case are charging; null where the model or its firmware doesn't say. */
    val charging: Triple<Boolean, Boolean, Boolean>? = null,
    /** 0 off, 1 noise cancelling, 2 ambient sound, 3 adaptive. */
    val noise: Int? = null,
    /** Ambient sound tuned to voices (2019 Buds). */
    val voiceFocus: Boolean? = null,
    /** 0 normal, then 1 Bass boost, 2 Soft, 3 Dynamic, 4 Clear, 5 Treble boost. */
    val preset: Int? = null,
    /** The touch-and-hold action numbers, left and right. */
    val holdLeft: Int? = null,
    val holdRight: Int? = null,
    val touchLocked: Boolean? = null,
    /** The gesture switches as the Buds2 and later keep them, kept whole to send back unchanged. */
    val gestures: Int? = null,
) {
    companion object {
        const val EXTENDED_STATUS = 0x61
        const val STATUS = 0x60

        /** The full status the earbuds send on connecting and after most changes. */
        fun extended(model: SamsungModel, p: ByteArray): SamsungStatus? {
            fun at(i: Int): Int? = p.getOrNull(i)?.toInt()?.and(0xFF)
            val revision = at(0) ?: return null
            val left = level(at(2) ?: return null)
            val right = level(at(3) ?: return null)
            val coupled = at(4) == 1
            if (model == SamsungModel.Buds) {
                val enabled = at(10) == 1
                // 0–4 are the presets, 5–9 the same ones while the phone plays Dolby Atmos.
                val preset = at(11)?.let { if (!enabled) 0 else (if (it > 4) it - 5 else it) + 1 }
                val wide = p.size >= 14
                return SamsungStatus(
                    revision, left, right, null, coupled,
                    noise = at(7)?.let { if (it == 1) 2 else 0 },
                    voiceFocus = at(8)?.let { it == 1 },
                    preset = preset,
                    holdLeft = at(if (wide) 13 else 12)?.let { if (wide) it shr 4 else it and 0x0F },
                    holdRight = at(if (wide) 13 else 12)?.let { it and 0x0F },
                    touchLocked = (if (wide) at(12) else at(12)?.shr(4))?.let { it != 0 },
                )
            }
            val case = at(7)?.let { level(it) }
            if (model == SamsungModel.BudsPlus) {
                return SamsungStatus(
                    revision, left, right, case, coupled,
                    noise = at(8)?.let { if (it == 1) 2 else 0 },
                    preset = at(11),
                    holdLeft = at(13)?.shr(4),
                    holdRight = at(13)?.and(0x0F),
                    touchLocked = at(12)?.let { it != 0 },
                )
            }
            val lockByte = at(10)
            val gestures = model.lock(revision) == SamsungModel.Lock.Gestures
            return SamsungStatus(
                revision, left, right, case, coupled,
                charging = if (model.chargingFrom(revision)) chargingAt(model, revision)?.let { at(it) }?.let { bits(it) } else null,
                noise = at(12)?.let { if (model == SamsungModel.BudsLive) (if (it == 1) 1 else 0) else it },
                preset = at(9),
                holdLeft = at(11)?.shr(4),
                holdRight = at(11)?.and(0x0F),
                // The gesture byte's top bit is "touch controls on".
                touchLocked = lockByte?.let { if (gestures) it and 0x80 == 0 else it == 1 },
                gestures = if (gestures) lockByte else null,
            )
        }

        /** The short status sent when a battery or where an earbud is changes. */
        fun basic(model: SamsungModel, p: ByteArray, revision: Int): SamsungStatus? {
            fun at(i: Int): Int? = p.getOrNull(i)?.toInt()?.and(0xFF)
            return if (model == SamsungModel.Buds) {
                SamsungStatus(revision, level(at(1) ?: return null), level(at(2) ?: return null), null, at(3) == 1)
            } else {
                SamsungStatus(
                    revision, level(at(1) ?: return null), level(at(2) ?: return null), at(6)?.let { level(it) }, at(3) == 1,
                    charging = if (model.chargingFrom(revision)) at(7)?.let { bits(it) } else null,
                )
            }
        }

        /**
         * Where the charging byte sits in the extended status. It follows a run of fields that grew with each model and
         * revision; for the Buds Core, Buds3 FE and Buds4 the layout isn't known well enough, so their charging comes
         * from the short status only.
         */
        private fun chargingAt(model: SamsungModel, revision: Int): Int? = when (model) {
            SamsungModel.Buds2 -> 36
            SamsungModel.Buds2Pro -> {
                var at = 34
                if (revision >= 1) at += 7
                if (revision >= 3) at += 1
                if (revision >= 8) at += 1
                at
            }
            SamsungModel.BudsFe -> 43
            SamsungModel.Buds3, SamsungModel.Buds3Pro -> 42
            else -> null
        }

        private fun bits(b: Int) = Triple(b and 0x10 != 0, b and 0x04 != 0, b and 0x01 != 0)

        private fun level(v: Int): Int? = v.takeIf { it in 1..100 }
    }
}

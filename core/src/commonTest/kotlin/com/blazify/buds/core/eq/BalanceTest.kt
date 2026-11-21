package com.blazify.buds.core.eq

import com.blazify.buds.core.soundcore.A3947
import com.blazify.buds.core.soundcore.generic.EqSetup
import com.blazify.buds.core.soundcore.generic.EqState
import com.blazify.buds.core.soundcore.generic.EqWrite
import com.blazify.buds.core.soundcore.generic.Parsed
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BalanceTest {
    private val curve = listOf(3.0, 2.0, 0.0, -1.0, 0.5, 2.0, 1.0, -2.0)

    @Test
    fun towardsTheRightTurnsTheLeftDown() {
        val (left, right) = Balance.split(curve, 2.5)
        assertEquals(curve.map { it - 2.5 }, left)
        assertEquals(curve, right)
        assertEquals(2.5, Balance.of(left, right))
        assertEquals(curve, Balance.curve(left, right))
    }

    @Test
    fun towardsTheLeftTurnsTheRightDown() {
        val (left, right) = Balance.split(curve, -4.0)
        assertEquals(curve, left)
        assertEquals(curve.map { it - 4.0 }, right)
        assertEquals(-4.0, Balance.of(left, right))
        assertEquals(curve, Balance.curve(left, right))
    }

    @Test
    fun centreIsTheSameBandsOnBoth() {
        assertEquals(curve to curve, Balance.split(curve, 0.0))
        assertEquals(0.0, Balance.of(curve, curve))
        // Ears that differ by more than one amount aren't a balance.
        assertEquals(0.0, Balance.of(curve, curve.mapIndexed { i, g -> g + i }))
        assertEquals(6.0, Balance.of(curve.map { it - 9 }, curve))
    }

    @Test
    fun theLiberty4NcSendsEachEarItsOwnBands() {
        val raw = ByteArray(140) { 0x78 }
        val left = IntArray(10) { if (it < 8) -30 else 0 }
        val right = IntArray(10) { 0 }
        val body = A3947.equalizerBody(raw, 0xFE, left, right)
        // The preset id and genre, then ten left bands and ten right bands.
        assertContentEquals(ByteArray(8) { 0x5A } + byteArrayOf(0x78, 0x78), body.copyOfRange(4, 14))
        assertContentEquals(ByteArray(10) { 0x78 }, body.copyOfRange(14, 24))
        // Without a right, both ears get the same, as before.
        assertContentEquals(A3947.equalizerBody(raw, 0xFE, left, left), A3947.equalizerBody(raw, 0xFE, left))
    }

    @Test
    fun soundcoreModelsSendTheSecondChannelApart() {
        val setup = EqSetup(EqSetup.PRESETS, EqWrite.Plain)
        val p = Parsed().apply { eq = EqState(0, listOf(IntArray(8), IntArray(8))) }
        val left = IntArray(8) { -20 }
        val right = IntArray(8)
        val step = setup.steps(p, com.blazify.buds.core.model.Equalizer.CUSTOM, left, right).single()
        step.apply(p)
        assertContentEquals(left, p.eq!!.channels[0])
        assertContentEquals(right, p.eq!!.channels[1])
        assertEquals(true, setup.read(p.eq!!).sides)
        assertEquals(2.0, Balance.of(setup.read(p.eq!!).left, setup.read(p.eq!!).right))
    }
}

package com.blazify.buds.core.huawei

import com.blazify.buds.core.link.LinuxBluetooth
import com.blazify.buds.core.model.Link
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test

/** Talks to real Huawei/Honor earbuds at HUAWEI_ADDRESS; skipped without it. */
class HuaweiLiveTest {
    @Test
    fun readsRealEarbuds() {
        val address = System.getenv("HUAWEI_ADDRESS") ?: return
        runBlocking {
            val buds = HuaweiBuds(
                "live", this,
                listOf({ LinuxBluetooth.open(address, HuaweiBuds.SERIAL) }, { LinuxBluetooth.rfcomm(address, 1) }),
                { println(it) },
            )
            buds.start()
            val state = withTimeout(20_000) { buds.state.first { it.link == Link.Connected && it.left != null && it.firmware != null } }
            println("STATE left=${state.left} right=${state.right} case=${state.case} model=${state.model} firmware=${state.firmware} serial=${state.serial?.take(4)}…")
            kotlinx.coroutines.delay(5_000)
            val more = buds.state.value
            println("SOUND ${more.sound?.mode}")
            println("EQ ${more.equalizer?.presetId} of ${more.equalizer?.presets?.map { it.id to it.name }}")
            println("TOUCH ${more.controls?.actions} choices ${more.controls?.availableFor}")
            println("TOGGLES ${more.toggles}")
            buds.close()
        }
    }
}

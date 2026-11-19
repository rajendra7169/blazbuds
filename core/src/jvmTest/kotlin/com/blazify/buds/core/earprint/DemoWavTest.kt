package com.blazify.buds.core.earprint

import java.io.File
import kotlin.test.Test

/** Writes the demo beat and a beep to WAV files in SOUNDS_DIR, to listen to them; only with that set. */
class DemoWavTest {
    @Test
    fun writeSounds() {
        val dir = System.getenv("SOUNDS_DIR")?.let(::File) ?: return
        dir.mkdirs()
        wav(File(dir, "demo.wav"), TestSounds.demo())
        wav(File(dir, "beep-1k-left.wav"), TestSounds.beeps(1000, -30.0, left = true, right = false))
    }

    private fun wav(file: File, samples: ShortArray) {
        val data = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, v -> data[i * 2] = v.toByte(); data[i * 2 + 1] = (v.toInt() shr 8).toByte() }
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(2); putInt(TestSounds.RATE); putInt(TestSounds.RATE * 4); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(data.size)
        }.array()
        file.writeBytes(header + data)
    }
}

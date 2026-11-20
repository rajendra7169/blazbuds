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
        wav(File(dir, "circling.wav"), TestSounds.circling())
        val beat = TestSounds.demo()
        for (mode in com.blazify.buds.core.spatial.SpatialMode.entries) {
            val spatial = com.blazify.buds.core.spatial.Spatializer(TestSounds.RATE).also { it.mode = mode }
            val input = FloatArray(beat.size) { beat[it] / 32768f }
            val output = FloatArray(beat.size)
            // In blocks, the way it runs on live sound.
            val block = 480
            var at = 0
            while (at < beat.size / 2) {
                val frames = minOf(block, beat.size / 2 - at)
                val inBlock = input.copyOfRange(at * 2, (at + frames) * 2)
                val outBlock = FloatArray(frames * 2)
                spatial.process(inBlock, outBlock, frames)
                outBlock.copyInto(output, at * 2)
                at += frames
            }
            wav(File(dir, "demo-3d-${mode.name.lowercase()}.wav"), ShortArray(output.size) { (output[it] * 32767).toInt().coerceIn(-32767, 32767).toShort() })
        }
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

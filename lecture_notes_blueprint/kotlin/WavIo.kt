package com.yourapp.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared WAV read helper. Reads 16-bit PCM mono WAV → normalized float32.
 * Auto-resamples if the WAV is not 16 kHz.
 */
object WavIo {
    fun readWavAsFloat(wav: File): FloatArray {
        RandomAccessFile(wav, "r").use { raf ->
            raf.seek(24); val sr = readIntLE(raf)
            raf.seek(40); val dataLen = readIntLE(raf)
            val bytes = ByteArray(dataLen)
            raf.read(bytes)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val out = FloatArray(dataLen / 2)
            for (i in out.indices) {
                out[i] = bb.short.toFloat() / Short.MAX_VALUE
            }
            return if (sr == 16000) out else linearResample(out, sr, 16000)
        }
    }

    private fun readIntLE(raf: RandomAccessFile): Int {
        val b = ByteArray(4); raf.read(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }

    private fun linearResample(input: FloatArray, srIn: Int, srOut: Int): FloatArray {
        val ratio = srOut.toDouble() / srIn
        val outLen = (input.size * ratio).toInt()
        val out = FloatArray(outLen)
        for (i in 0 until outLen) {
            val srcIdx = i / ratio
            val lo = srcIdx.toInt().coerceIn(0, input.size - 1)
            val hi = (lo + 1).coerceIn(0, input.size - 1)
            val frac = (srcIdx - lo).toFloat()
            out[i] = input[lo] * (1 - frac) + input[hi] * frac
        }
        return out
    }
}

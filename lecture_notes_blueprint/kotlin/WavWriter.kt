package com.yourapp.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes float32 PCM samples into a WAV file (16-bit PCM on disk).
 *
 * Streaming design:
 *   - Header is written up front with placeholders for size fields.
 *   - On close(), header is patched with actual sizes.
 *   - flush() forces the current data to disk — call every 30s so crashes don't lose audio.
 *
 * NOTE: We write 16-bit PCM to disk (not float32) because Whisper/sherpa-onnx re-reads
 * WAV as 16-bit anyway, and it halves file size. Conversion is done here.
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int
) {
    private var out: BufferedOutputStream? = null
    private var bytesWritten = 0

    fun open() {
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        out = BufferedOutputStream(fos, 8192)

        // Write placeholder header (44 bytes)
        writeHeader(dataSize = 0)
    }

    fun writeSamples(samples: FloatArray, count: Int) {
        val bb = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val v = (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            bb.putShort(v)
        }
        out?.write(bb.array())
        bytesWritten += count * 2
    }

    fun flush() {
        out?.flush()
        // Update WAV header on disk with current size (uses RandomAccessFile)
        patchHeader()
    }

    fun close() {
        out?.flush()
        out?.close()
        out = null
        patchHeader()
    }

    // ─── internals ───────────────────────────────────────────────────────
    private fun writeHeader(dataSize: Int) {
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray())
        bb.putInt(36 + dataSize)              // chunk size
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16)                         // subchunk1 size (PCM)
        bb.putShort(1)                        // audio format = PCM
        bb.putShort(1)                        // num channels = 1 (mono)
        bb.putInt(sampleRate)                 // sample rate
        bb.putInt(sampleRate * 2)             // byte rate = SR * channels * bps/8
        bb.putShort(2)                        // block align = channels * bps/8
        bb.putShort(16)                       // bits per sample
        bb.put("data".toByteArray())
        bb.putInt(dataSize)                   // data subchunk size
        out?.write(bb.array())
    }

    private fun patchHeader() {
        // Rewrite bytes 4..7 (chunk size) and 40..43 (data size)
        try {
            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(4);  raf.write(intToLE(36 + bytesWritten))
                raf.seek(40); raf.write(intToLE(bytesWritten))
            }
        } catch (_: Exception) { /* file not yet flushed to disk; safe to ignore */ }
    }

    private fun intToLE(v: Int): ByteArray = byteArrayOf(
        (v and 0xff).toByte(),
        ((v shr 8) and 0xff).toByte(),
        ((v shr 16) and 0xff).toByte(),
        ((v shr 24) and 0xff).toByte()
    )
}

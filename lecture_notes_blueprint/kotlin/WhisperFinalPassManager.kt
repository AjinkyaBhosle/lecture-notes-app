package com.yourapp.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Non-streaming high-accuracy pass using Whisper-small.
 * Chunked at 30 sec with 5 sec overlap — avoids repetition-loop bugs of long-form autoregressive ASR.
 *
 * Returns a list of Segment(startMs, endMs, text).
 * These are speaker-agnostic — DiarizationManager assigns speakers later.
 *
 * Model files at:
 *   filesDir/models/asr/whisper_small_en/
 *     small.en-encoder.int8.onnx
 *     small.en-decoder.int8.onnx
 *     small.en-tokens.txt
 */
class WhisperFinalPassManager(private val context: Context) {

    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    private var recognizer: OfflineRecognizer? = null

    fun init() {
        val modelDir = File(context.getExternalFilesDir(null), "models/asr/whisper_small_en")
        require(modelDir.exists()) { "Whisper models not downloaded yet: $modelDir" }

        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(modelDir, "small.en-encoder.int8.onnx").absolutePath,
                    decoder = File(modelDir, "small.en-decoder.int8.onnx").absolutePath,
                    language = "en",
                    task = "transcribe",
                    tailPaddings = 1000
                ),
                tokens = File(modelDir, "small.en-tokens.txt").absolutePath,
                numThreads = 4,
                provider = "cpu",
                modelType = "whisper"
            )
        )
        recognizer = OfflineRecognizer(config)
    }

    /**
     * Transcribe an entire WAV file (any duration).
     * Progress reports 0.0..1.0.
     */
    suspend fun transcribe(
        wavFile: File,
        onProgress: (Float) -> Unit = {}
    ): List<Segment> {
        require(recognizer != null) { "call init() first" }
        val samples = readWavAsFloat(wavFile)                       // 16 kHz mono
        val totalMs = (samples.size * 1000L) / 16000

        val chunkSize = 30 * 16000                                  // 30 sec
        val overlap   = 5  * 16000                                  //  5 sec

        val segments = mutableListOf<Segment>()
        var start = 0
        var chunkIdx = 0
        val totalChunks = ((samples.size - overlap).coerceAtLeast(1) + (chunkSize - overlap) - 1) /
                          (chunkSize - overlap)

        while (start < samples.size) {
            val end = minOf(start + chunkSize, samples.size)
            val slice = samples.copyOfRange(start, end)

            val stream = recognizer!!.createStream()
            stream.acceptWaveform(slice, 16000)
            recognizer!!.decode(stream)
            val text = recognizer!!.getResult(stream).text.trim()
            stream.release()

            if (text.isNotEmpty()) {
                val startMs = (start * 1000L) / 16000
                val endMs   = (end   * 1000L) / 16000
                segments += Segment(startMs, endMs, text)
            }

            chunkIdx++
            onProgress(chunkIdx.toFloat() / totalChunks)

            if (end == samples.size) break
            start += chunkSize - overlap
        }

        return mergeOverlappingSegments(segments)
    }

    fun release() {
        recognizer?.release()
        recognizer = null
    }

    // ─── helpers ─────────────────────────────────────────────────────────
    /** Merge overlapping chunks by removing duplicate words at the boundary. */
    private fun mergeOverlappingSegments(segs: List<Segment>): List<Segment> {
        if (segs.size <= 1) return segs
        val merged = mutableListOf(segs.first())
        for (i in 1 until segs.size) {
            val prev = merged.last()
            val cur  = segs[i]
            // If ranges overlap (they will, by design), dedupe last N words of prev against first N of cur
            val (a, b) = dedupBoundary(prev.text, cur.text, maxN = 6)
            merged[merged.size - 1] = prev.copy(text = a)
            merged += cur.copy(text = b)
        }
        return merged
    }

    private fun dedupBoundary(prev: String, cur: String, maxN: Int): Pair<String, String> {
        val pW = prev.split(" ")
        val cW = cur.split(" ")
        for (n in minOf(maxN, pW.size, cW.size) downTo 2) {
            val tail = pW.takeLast(n).joinToString(" ").lowercase()
            val head = cW.take(n).joinToString(" ").lowercase()
            if (tail == head) {
                return pW.joinToString(" ") to cW.drop(n).joinToString(" ")
            }
        }
        return prev to cur
    }

    /** Read a WAV (assumes 16-bit PCM mono, any sample rate) → normalized float32 at 16 kHz. */
    private fun readWavAsFloat(wav: File): FloatArray {
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
            // Simple resample if sr != 16000 (linear interp — good enough)
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

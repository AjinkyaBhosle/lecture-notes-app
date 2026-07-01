package com.yourapp.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.yourapp.db.AppDatabase
import com.yourapp.db.Speaker
import java.io.File
import kotlin.math.sqrt

/**
 * Cross-lecture speaker identity via WeSpeaker embeddings.
 *
 * On first lecture: user renames "SPEAKER_00" → "Prof. Sharma". We compute the average
 * embedding of that speaker's audio and store it in Room DB.
 *
 * On subsequent lectures: for each diarized speaker, compute average embedding, then
 * find the closest known speaker via cosine similarity. If sim > 0.7, auto-tag.
 */
class VoiceIdManager(private val context: Context, private val db: AppDatabase) {

    private var extractor: SpeakerEmbeddingExtractor? = null

    fun init() {
        val modelFile = File(context.getExternalFilesDir(null), "models/voice_id/wespeaker_en.onnx")
        require(modelFile.exists()) { "WeSpeaker model not downloaded yet" }

        extractor = SpeakerEmbeddingExtractor(SpeakerEmbeddingExtractorConfig(
            model = modelFile.absolutePath,
            numThreads = 2,
            provider = "cpu"
        ))
    }

    /**
     * Compute average embedding for a speaker across all their segments in a lecture.
     * Called for each unique speakerId in a diarized lecture.
     */
    fun computeAverageEmbedding(
        samples: FloatArray,
        speakerSegments: List<DiarizationManager.SpeakerSegment>
    ): FloatArray {
        val ext = extractor ?: error("call init() first")
        val embs = mutableListOf<FloatArray>()

        for (seg in speakerSegments) {
            val startIdx = (seg.startMs * 16).toInt().coerceIn(0, samples.size - 1)
            val endIdx   = (seg.endMs   * 16).toInt().coerceIn(0, samples.size)
            if (endIdx - startIdx < 16000) continue         // < 1 sec — too short

            val slice = samples.copyOfRange(startIdx, endIdx)
            val stream = ext.createStream()
            stream.acceptWaveform(slice, 16000)
            stream.inputFinished()
            val emb = ext.compute(stream)
            stream.release()
            embs += emb
        }

        return average(embs)
    }

    /**
     * Given a new embedding, find best matching known speaker.
     * Returns null if no match above threshold.
     */
    suspend fun findBestMatch(
        newEmbedding: FloatArray,
        threshold: Float = 0.7f
    ): Speaker? {
        val known = db.speakerDao().getAll()
        var best: Speaker? = null
        var bestSim = threshold
        for (sp in known) {
            val sim = cosine(newEmbedding, sp.embedding)
            if (sim > bestSim) { bestSim = sim; best = sp }
        }
        return best
    }

    suspend fun saveSpeaker(name: String, embedding: FloatArray): Long {
        return db.speakerDao().insert(Speaker(name = name, embedding = embedding))
    }

    fun release() {
        extractor?.release()
        extractor = null
    }

    // ─── math ────────────────────────────────────────────────────────────
    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]
        }
        return (dot / (sqrt(na) * sqrt(nb) + 1e-8f))
    }

    private fun average(embs: List<FloatArray>): FloatArray {
        if (embs.isEmpty()) return FloatArray(256)      // WeSpeaker dim = 256
        val out = FloatArray(embs[0].size)
        for (e in embs) for (i in e.indices) out[i] += e[i]
        for (i in out.indices) out[i] /= embs.size
        return out
    }
}

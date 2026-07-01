package com.yourapp.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Wraps sherpa-onnx OfflinePunctuation.
 * Given raw text, returns text with periods, commas, question marks, capitalization.
 *
 * Model at: filesDir/models/punctuation/
 *   model.int8.onnx
 *   config.yaml (bundled by sherpa-onnx tar)
 */
class PunctuationManager(private val context: Context) {

    private var punct: OfflinePunctuation? = null

    fun init() {
        val dir = File(context.getExternalFilesDir(null), "models/punctuation")
        require(dir.exists()) { "Punctuation model not downloaded yet" }

        val config = OfflinePunctuationConfig(
            model = OfflinePunctuationModelConfig(
                ctTransformer = File(dir, "model.int8.onnx").absolutePath,
                numThreads = 2,
                provider = "cpu"
            )
        )
        punct = OfflinePunctuation(config)
    }

    /** Add punctuation to raw text. Idempotent — safe to call on already-punctuated text. */
    fun punctuate(text: String): String {
        if (text.isBlank()) return text
        return punct?.addPunctuation(text) ?: text
    }

    /** Apply punctuation to each segment in a transcript. */
    fun punctuateSegments(
        segments: List<DiarizationManager.AssignedSegment>
    ): List<DiarizationManager.AssignedSegment> {
        return segments.map { it.copy(text = punctuate(it.text)) }
    }

    fun release() {
        punct?.release()
        punct = null
    }
}

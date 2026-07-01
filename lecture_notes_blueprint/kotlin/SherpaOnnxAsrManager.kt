package com.yourapp.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Wraps sherpa-onnx OnlineRecognizer (streaming Zipformer).
 * Provides live captions while recording.
 *
 * Model files (downloaded via ModelDownloadManager) expected at:
 *   filesDir/models/asr/streaming_zipformer_en/
 *     encoder-epoch-99-avg-1.chunk-16-left-64.int8.onnx
 *     decoder-epoch-99-avg-1.chunk-16-left-64.int8.onnx
 *     joiner-epoch-99-avg-1.chunk-16-left-64.int8.onnx
 *     tokens.txt
 *
 * Feed samples with acceptSamples(FloatArray) — must be 16 kHz mono float32 in range [-1, 1].
 * Get current partial text with getPartialText().
 */
class SherpaOnnxAsrManager(private val context: Context) {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    fun init() {
        val modelDir = File(context.getExternalFilesDir(null), "models/asr/streaming_zipformer_en")
        require(modelDir.exists()) { "ASR models not downloaded yet: $modelDir" }

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = 16000,
                featureDim = 80
            ),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(modelDir, "encoder-epoch-99-avg-1.chunk-16-left-64.int8.onnx").absolutePath,
                    decoder = File(modelDir, "decoder-epoch-99-avg-1.chunk-16-left-64.int8.onnx").absolutePath,
                    joiner  = File(modelDir, "joiner-epoch-99-avg-1.chunk-16-left-64.int8.onnx").absolutePath
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = 2,
                provider = "cpu",           // "nnapi" if you want to try NPU (some devices flaky)
                modelType = "zipformer2"
            ),
            endpointConfig = EndpointConfig(
                rule1 = EndpointRule(false, 2.4f, 0.0f),   // 2.4s of silence ends utterance
                rule2 = EndpointRule(true,  1.4f, 0.0f),
                rule3 = EndpointRule(false, 0.0f, 20.0f)
            ),
            enableEndpoint = true,
            decodingMethod = "greedy_search",
            maxActivePaths = 4
        )

        recognizer = OnlineRecognizer(config).also {
            stream = it.createStream()
        }
    }

    /** Feed 100ms chunks of mic audio. Call from your recorder loop. */
    fun acceptSamples(samples: FloatArray) {
        val r = recognizer ?: return
        val s = stream ?: return

        s.acceptWaveform(samples, sampleRate = 16000)

        while (r.isReady(s)) {
            r.decode(s)
        }

        // If an utterance ended (endpoint detected), reset for next utterance
        if (r.isEndpoint(s)) {
            r.reset(s)
        }
    }

    /** Get the current best transcription text. Safe to call frequently. */
    fun getPartialText(): String {
        val r = recognizer ?: return ""
        val s = stream ?: return ""
        return r.getResult(s).text.trim()
    }

    fun release() {
        stream?.release()
        recognizer?.release()
        stream = null
        recognizer = null
    }
}

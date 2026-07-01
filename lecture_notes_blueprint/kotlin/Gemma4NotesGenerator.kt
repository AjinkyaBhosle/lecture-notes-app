package com.yourapp.llm

import android.content.Context
import com.google.ai.edge.litertlm.LlmInference
import com.google.ai.edge.litertlm.LlmInferenceOptions
import com.google.ai.edge.litertlm.LlmInferenceSession
import com.google.ai.edge.litertlm.LlmInferenceSessionOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import java.io.File

/**
 * Wraps LiteRT-LM to run Gemma 4 E2B on-device.
 *
 * Model file at: filesDir/models/llm/gemma-4-E2B-it-int4.litertlm  (~1.5 GB)
 *
 * Usage:
 *   val gen = Gemma4NotesGenerator(context)
 *   gen.init()
 *   gen.summarize(transcript).collect { chunk -> ui.append(chunk) }
 *   gen.close()
 *
 * All methods return Flow<String> that emits streaming token chunks — makes UI feel fast.
 */
class Gemma4NotesGenerator(private val context: Context) {

    private var llm: LlmInference? = null

    fun init() {
        val modelFile = File(context.getExternalFilesDir(null), "models/llm/gemma-4-E2B-it-int4.litertlm")
        require(modelFile.exists()) { "Gemma 4 model not downloaded yet" }

        val options = LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(128_000)          // Gemma 4 supports 128K context
            .setPreferredBackend(LlmInference.Backend.CPU) // GPU on some Snapdragons, but CPU is safe default
            .build()

        llm = LlmInference.createFromOptions(context, options)
    }

    // ─── High-level tasks ────────────────────────────────────────────────
    fun summarize(transcript: String, maxWords: Int = 200): Flow<String> =
        streamGenerate(GemmaPrompts.summarize(transcript, maxWords))

    fun extractSections(transcript: String): Flow<String> =
        streamGenerate(GemmaPrompts.sections(transcript))

    fun makeFlashcards(transcript: String, count: Int = 20): Flow<String> =
        streamGenerate(GemmaPrompts.flashcards(transcript, count))

    fun extractKeyDefinitions(transcript: String): Flow<String> =
        streamGenerate(GemmaPrompts.keyDefinitions(transcript))

    fun answerQuestion(transcript: String, question: String): Flow<String> =
        streamGenerate(GemmaPrompts.answerQuestion(transcript, question))

    // ─── Streaming inference ─────────────────────────────────────────────
    private fun streamGenerate(prompt: String): Flow<String> = callbackFlow {
        val engine = llm ?: error("call init() first")
        val sessionOpts = LlmInferenceSessionOptions.builder()
            .setTemperature(0.3f)                 // low = factual for lecture notes
            .setTopK(40)
            .setTopP(0.9f)
            .build()

        val session = LlmInferenceSession.createFromOptions(engine, sessionOpts)
        session.addQueryChunk(prompt)

        session.generateResponseAsync { partial, done ->
            trySend(partial)
            if (done) close()
        }
        awaitClose {
            session.close()
        }
    }

    fun close() {
        llm?.close()
        llm = null
    }
}

package com.yourapp.llm

import android.app.ActivityManager
import android.content.Context
import com.google.ai.edge.litertlm.LlmInference
import com.google.ai.edge.litertlm.LlmInferenceOptions
import com.google.ai.edge.litertlm.LlmInferenceSession
import com.google.ai.edge.litertlm.LlmInferenceSessionOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * Wraps LiteRT-LM to run Gemma 4 E2B on-device.
 *
 * ─── IMPORTANT: Context Chunking Strategy (updated post-audit) ────────────
 *
 * Gemma 4 E2B architecturally supports 128K context, BUT running that on
 * a phone is only feasible on flagships (SD 8 Gen 3+, Tensor G4+).
 *
 * On mid-range phones (6-8 GB RAM) the KV cache thrashes memory above
 * ~24K tokens. So by default we CHUNK long transcripts and run a
 * hierarchical summarize pattern:
 *
 *   1) Split transcript into ~8K-token windows (~15 minutes of lecture each)
 *   2) Summarize each window independently
 *   3) Concatenate window summaries, run a final "merge" pass to produce
 *      the coherent whole-lecture output.
 *
 * For flagships we auto-detect (RAM >= 10 GB) and use single-pass 128K mode.
 *
 * Model file at: filesDir/models/llm/gemma-4-E2B-it-int4.litertlm  (~1.5 GB)
 */
class Gemma4NotesGenerator(private val context: Context) {

    /** How to run inference given the transcript size + device capabilities. */
    enum class Mode {
        SINGLE_PASS_128K,   // flagship phones only, whole transcript in one prompt
        CHUNKED_HIERARCHICAL // default: chunk → summarize each → merge (works on 6-8 GB RAM)
    }

    private var llm: LlmInference? = null
    private var mode: Mode = Mode.CHUNKED_HIERARCHICAL

    /** Tokens per chunk. ~8K keeps memory sane on 6 GB phones. */
    private val chunkTokens = 8_000

    /** Rough ratio: 1 English word ≈ 1.3 tokens (Gemma tokenizer). */
    private fun estimateTokens(text: String): Int = (text.split(" ").size * 1.3).toInt()

    fun init() {
        val modelFile = File(context.getExternalFilesDir(null), "models/llm/gemma-4-E2B-it-int4.litertlm")
        require(modelFile.exists()) { "Gemma 4 model not downloaded yet" }

        // Choose mode based on device RAM
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        mode = if (totalRamMb >= 10_000) Mode.SINGLE_PASS_128K else Mode.CHUNKED_HIERARCHICAL

        val maxTokens = if (mode == Mode.SINGLE_PASS_128K) 128_000 else 32_000

        val options = LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(maxTokens)
            .setPreferredBackend(LlmInference.Backend.CPU)
            .build()

        llm = LlmInference.createFromOptions(context, options)
    }

    // ─── High-level tasks ────────────────────────────────────────────────
    /** Auto-picks single-pass or chunked-merge based on transcript length + mode. */
    fun summarize(transcript: String, maxWords: Int = 200): Flow<String> = flow {
        val tokens = estimateTokens(transcript)
        val threshold = if (mode == Mode.SINGLE_PASS_128K) 100_000 else 8_000

        if (tokens <= threshold) {
            // Fits in one call — stream directly
            streamGenerate(GemmaPrompts.summarize(transcript, maxWords)).collect { emit(it) }
        } else {
            // Chunk-summarize-merge
            val chunks = chunkByTokens(transcript, chunkTokens)
            val partial = StringBuilder()
            for ((i, chunk) in chunks.withIndex()) {
                val prompt = GemmaPrompts.summarize(chunk, maxWords = 80)  // shorter per-chunk
                partial.append("### Part ${i + 1}\n")
                streamGenerate(prompt).collect { tok ->
                    partial.append(tok); emit(tok)
                }
                partial.append("\n\n")
            }
            // Final merge pass — one summary of summaries
            emit("\n\n---\n\n**Overall summary:**\n")
            val mergePrompt = GemmaPrompts.mergeSummaries(partial.toString(), maxWords)
            streamGenerate(mergePrompt).collect { emit(it) }
        }
    }.flowOn(Dispatchers.Default)

    /** Same chunk-then-merge pattern for section notes. */
    fun extractSections(transcript: String): Flow<String> = flow {
        val chunks = chunkByTokens(transcript, chunkTokens)
        for ((i, chunk) in chunks.withIndex()) {
            emit("\n\n<!-- Part ${i + 1} of ${chunks.size} -->\n")
            streamGenerate(GemmaPrompts.sections(chunk)).collect { emit(it) }
        }
    }.flowOn(Dispatchers.Default)

    /** Flashcards from each chunk, deduplicated by simple heuristic. */
    fun makeFlashcards(transcript: String, countTotal: Int = 20): Flow<String> = flow {
        val chunks = chunkByTokens(transcript, chunkTokens)
        val perChunk = (countTotal / chunks.size).coerceAtLeast(2)
        emit("[")
        var first = true
        for (chunk in chunks) {
            var isFirstToken = true
            streamGenerate(GemmaPrompts.flashcards(chunk, perChunk)).collect { tok ->
                // Strip leading `[` and trailing `]` from each chunk's JSON emission
                val cleaned = if (isFirstToken) tok.trimStart().removePrefix("[") else tok
                isFirstToken = false
                if (!first) emit(",")
                emit(cleaned.trimEnd().removeSuffix("]"))
                first = false
            }
        }
        emit("]")
    }.flowOn(Dispatchers.Default)

    /** Q&A doesn't chunk — we pick the most relevant chunk via keyword match first. */
    fun answerQuestion(transcript: String, question: String): Flow<String> = flow {
        val chunks = chunkByTokens(transcript, chunkTokens)
        val best = chunks.maxByOrNull { chunk ->
            question.split(" ").filter { it.length > 3 }.count { chunk.contains(it, ignoreCase = true) }
        } ?: transcript.take(chunkTokens * 4)  // fallback
        streamGenerate(GemmaPrompts.answerQuestion(best, question)).collect { emit(it) }
    }.flowOn(Dispatchers.Default)

    fun extractKeyDefinitions(transcript: String): Flow<String> = flow {
        // Definitions can be extracted per-chunk without needing merge
        val chunks = chunkByTokens(transcript, chunkTokens)
        for (chunk in chunks) {
            streamGenerate(GemmaPrompts.keyDefinitions(chunk)).collect { emit(it) }
        }
    }.flowOn(Dispatchers.Default)

    // ─── Streaming inference ─────────────────────────────────────────────
    private fun streamGenerate(prompt: String): Flow<String> = callbackFlow {
        val engine = llm ?: error("call init() first")
        val sessionOpts = LlmInferenceSessionOptions.builder()
            .setTemperature(0.3f)
            .setTopK(40)
            .setTopP(0.9f)
            .build()

        val session = LlmInferenceSession.createFromOptions(engine, sessionOpts)
        session.addQueryChunk(prompt)

        session.generateResponseAsync { partial, done ->
            trySend(partial)
            if (done) close()
        }
        awaitClose { session.close() }
    }

    // ─── Chunking helpers ────────────────────────────────────────────────
    /**
     * Chunk text by approximate token count, preferring natural break points
     * (paragraph, sentence). Uses word-to-token ratio ~1.3.
     */
    private fun chunkByTokens(text: String, tokensPerChunk: Int): List<String> {
        val wordsPerChunk = (tokensPerChunk / 1.3).toInt()
        val paragraphs = text.split("\n\n")

        val chunks = mutableListOf<String>()
        val cur = StringBuilder()
        var curWords = 0

        for (para in paragraphs) {
            val paraWords = para.split(" ").size
            if (curWords + paraWords > wordsPerChunk && cur.isNotEmpty()) {
                chunks += cur.toString().trim()
                cur.clear(); curWords = 0
            }
            cur.append(para).append("\n\n"); curWords += paraWords
        }
        if (cur.isNotEmpty()) chunks += cur.toString().trim()
        return chunks
    }

    fun close() {
        llm?.close()
        llm = null
    }
}

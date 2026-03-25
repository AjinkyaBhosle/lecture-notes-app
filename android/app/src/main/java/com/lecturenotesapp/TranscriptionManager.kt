package com.lecturenotesapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptionManager(private val context: Context) {
    
    private val indicWhisperManager = IndicWhisperManager(context)
    private val murilManager = MuRILManager(context)
    private val textCleanupUtil = TextCleanupUtil()

    suspend fun transcribeAudio(
        audioFile: File,
        language: String = "hi"
    ): TranscriptionResult = withContext(Dispatchers.Default) {
        try {
            Log.d("TranscriptionManager", "Starting transcription for: \\$\{audioFile.name}\")
            
            // Step 1: Transcribe with IndicWhisper
            val rawText = indicWhisperManager.transcribe(audioFile, language)
            Log.d("TranscriptionManager", "Raw transcription: \\$\{rawText.take(100)}...\")
            
            // Step 2: Clean up text (punctuation, capitalization)
            val cleanedText = textCleanupUtil.cleanupText(rawText)
            Log.d("TranscriptionManager", "Cleaned text: \\$\{cleanedText.take(100)}...\")
            
            // Step 3: Understand and organize with MuRIL
            val keyPoints = murilManager.extractKeyPoints(cleanedText)
            Log.d("TranscriptionManager", "Extracted \\$\{keyPoints.size} key points")
            
            TranscriptionResult(
                success = true,
                fullText = cleanedText,
                keyPoints = keyPoints,
                language = language,
                error = null
            )
        } catch (e: Exception) {
            Log.e("TranscriptionManager", "Transcription error: \\$\{e.message}\")
            TranscriptionResult(
                success = false,
                fullText = "",
                keyPoints = emptyList(),
                language = language,
                error = e.message ?: "Unknown error"
            )
        }
    }

    fun getAvailableLanguages(): List<String> = listOf("en", "hi", "mr")
}

data class TranscriptionResult(
    val success: Boolean,
    val fullText: String,
    val keyPoints: List<String>,
    val language: String,
    val error: String? = null
)
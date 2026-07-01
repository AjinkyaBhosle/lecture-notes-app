package com.yourapp.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yourapp.asr.*
import com.yourapp.db.AppDatabase
import com.yourapp.db.Lecture
import com.yourapp.db.Segment
import java.io.File

/**
 * The whole post-recording pipeline in one WorkManager job.
 *
 *   1) Whisper-small final pass (chunked, no repetition loops)
 *   2) Diarization (speaker segments)
 *   3) Assign speakers to Whisper segments
 *   4) Voice ID (match against known speakers, auto-tag)
 *   5) Punctuation
 *   6) Save to Room DB
 *   7) Show "Lecture ready" notification
 *
 * Notes generation (Gemma 4) is a SEPARATE worker triggered when user opens NotesScreen —
 * we don't want to burn battery on notes for lectures they never open.
 */
class PostProcessingWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val wavPath = inputData.getString("wavPath") ?: return Result.failure()
        val title   = inputData.getString("lectureTitle") ?: "Untitled"
        val wavFile = File(wavPath)
        if (!wavFile.exists()) return Result.failure()

        Log.i("PostProc", "Starting for $wavPath")

        val whisper = WhisperFinalPassManager(ctx).also { it.init() }
        val diar    = DiarizationManager(ctx).also { it.init() }
        val punct   = PunctuationManager(ctx).also { it.init() }
        val db      = AppDatabase.get(ctx)
        val voiceId = VoiceIdManager(ctx, db).also { it.init() }

        try {
            // 1) Whisper
            Log.i("PostProc", "1/5 Whisper transcribing…")
            val whisperSegs = whisper.transcribe(wavFile) { p ->
                setProgress(androidx.work.workDataOf("stage" to "whisper", "progress" to p))
            }

            // 2) Diarization
            Log.i("PostProc", "2/5 Diarizing…")
            val speakerSegs = diar.diarize(wavFile) { p ->
                setProgress(androidx.work.workDataOf("stage" to "diarization", "progress" to p))
            }

            // 3) Merge
            Log.i("PostProc", "3/5 Assigning speakers…")
            val assigned = diar.assignSpeakersToTranscript(whisperSegs, speakerSegs)

            // 4) Voice ID (auto-tag known speakers)
            Log.i("PostProc", "4/5 Matching known voices…")
            val samples = WavIo.readWavAsFloat(wavFile)
            val speakerNames = mutableMapOf<Int, String>()
            for (spk in speakerSegs.groupBy { it.speakerId }) {
                val emb = voiceId.computeAverageEmbedding(samples, spk.value)
                val known = voiceId.findBestMatch(emb)
                speakerNames[spk.key] = known?.name ?: "SPEAKER_${spk.key.toString().padStart(2, '0')}"
            }

            // 5) Punctuation
            Log.i("PostProc", "5/5 Punctuating…")
            val punctuated = punct.punctuateSegments(assigned)

            // 6) Save to DB
            val lectureId = db.lectureDao().insert(Lecture(
                title = title,
                audioPath = wavPath,
                durationMs = punctuated.maxOfOrNull { it.endMs } ?: 0L,
                createdAt = System.currentTimeMillis()
            ))
            val segments = punctuated.map { seg ->
                Segment(
                    lectureId = lectureId,
                    startMs = seg.startMs,
                    endMs = seg.endMs,
                    speakerName = speakerNames[seg.speakerId] ?: "SPEAKER",
                    text = seg.text
                )
            }
            db.segmentDao().insertAll(segments)

            Log.i("PostProc", "Done! ${segments.size} segments saved.")
            return Result.success()
        } catch (e: Exception) {
            Log.e("PostProc", "Failed", e)
            return Result.retry()
        } finally {
            whisper.release()
            diar.release()
            punct.release()
            voiceId.release()
        }
    }
}

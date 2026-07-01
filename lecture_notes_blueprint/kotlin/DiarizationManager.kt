package com.yourapp.asr

import android.content.Context
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Wraps sherpa-onnx OfflineSpeakerDiarization.
 * Given a WAV file, returns a list of (startMs, endMs, speakerLabel) segments.
 *
 * Models expected at:
 *   filesDir/models/diarization/segmentation/model.onnx
 *   filesDir/models/diarization/speaker_embedding.onnx
 *
 * Speaker labels are integers: 0, 1, 2, ... (rename in UI later.)
 */
class DiarizationManager(private val context: Context) {

    data class SpeakerSegment(val startMs: Long, val endMs: Long, val speakerId: Int)

    private var diarizer: OfflineSpeakerDiarization? = null

    /**
     * @param numSpeakers if known (e.g. teacher-only lecture = 1). -1 = auto-detect.
     * @param clusterThreshold 0.5 = looser (more speakers), 0.7 = tighter (fewer). Default 0.5.
     */
    fun init(numSpeakers: Int = -1, clusterThreshold: Float = 0.5f) {
        val segDir = File(context.getExternalFilesDir(null), "models/diarization/segmentation")
        val embFile = File(context.getExternalFilesDir(null), "models/diarization/speaker_embedding.onnx")
        require(segDir.exists() && embFile.exists()) { "Diarization models not downloaded yet" }

        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = File(segDir, "model.onnx").absolutePath
                ),
                numThreads = 2,
                provider = "cpu"
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = embFile.absolutePath,
                numThreads = 2,
                provider = "cpu"
            ),
            clustering = FastClusteringConfig(
                numClusters = numSpeakers,        // -1 = auto
                threshold = clusterThreshold
            ),
            minDurationOn = 0.3f,
            minDurationOff = 0.5f
        )

        diarizer = OfflineSpeakerDiarization(config)
    }

    /** Diarize an entire WAV file. */
    suspend fun diarize(
        wavFile: File,
        onProgress: (Float) -> Unit = {}
    ): List<SpeakerSegment> {
        val d = diarizer ?: error("call init() first")
        val samples = WavIo.readWavAsFloat(wavFile)                 // 16 kHz mono float32
        val result = d.process(samples) { progress ->
            onProgress(progress); true
        }
        return result.map { seg ->
            SpeakerSegment(
                startMs = (seg.start * 1000).toLong(),
                endMs   = (seg.end   * 1000).toLong(),
                speakerId = seg.speaker
            )
        }
    }

    /**
     * Combine Whisper text segments with speaker segments.
     * For each Whisper segment, find the speaker whose time range overlaps most.
     */
    fun assignSpeakersToTranscript(
        transcript: List<WhisperFinalPassManager.Segment>,
        speakers: List<SpeakerSegment>
    ): List<AssignedSegment> {
        return transcript.map { t ->
            val bestSpeaker = speakers.maxByOrNull { s ->
                val overlap = minOf(t.endMs, s.endMs) - maxOf(t.startMs, s.startMs)
                overlap.coerceAtLeast(0)
            }?.speakerId ?: -1
            AssignedSegment(t.startMs, t.endMs, bestSpeaker, t.text)
        }
    }

    fun release() {
        diarizer?.release()
        diarizer = null
    }

    data class AssignedSegment(
        val startMs: Long,
        val endMs: Long,
        val speakerId: Int,
        val text: String
    )
}

package com.yourapp.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.File

/**
 * Records microphone audio as 16 kHz mono float32 PCM.
 *
 * Emits two things:
 *   1. A Flow<FloatArray> — chunks of ~100 ms of audio for feeding into sherpa-onnx ASR.
 *   2. Writes a rolling WAV file to disk every [FLUSH_INTERVAL_MS] ms (crash-safety).
 *
 * Also reports RMS amplitude for waveform UI via [amplitudeFlow].
 *
 * Usage:
 *   val recorder = AudioRecorder(outputWavFile = File(...))
 *   recorder.start()
 *   recorder.pcmFlow.collect { chunk -> asr.acceptSamples(chunk) }
 *   recorder.stop()
 */
class AudioRecorder(
    private val outputWavFile: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT
        private const val FLUSH_INTERVAL_MS = 30_000L  // flush WAV every 30 sec
    }

    private val minBufferBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, ENCODING)
    private val readBuf = FloatArray(minBufferBytes)  // samples not bytes; big enough

    private var recorder: AudioRecord? = null
    private var wavWriter: WavWriter? = null
    private var recordingJob: Job? = null
    private var flushJob: Job? = null

    // Public streams
    private val _pcmChannel = Channel<FloatArray>(Channel.BUFFERED)
    val pcmFlow: Flow<FloatArray> = _pcmChannel.receiveAsFlow()

    private val _amplitudeChannel = Channel<Float>(Channel.CONFLATED)
    val amplitudeFlow: Flow<Float> = _amplitudeChannel.receiveAsFlow()

    // ─── Public API ──────────────────────────────────────────────────────
    @SuppressLint("MissingPermission")  // caller must ensure RECORD_AUDIO
    fun start() {
        if (recorder != null) return
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,  // best for speech
            SAMPLE_RATE, CHANNEL_CONFIG, ENCODING,
            minBufferBytes * 4  // 4x for headroom
        ).also { it.startRecording() }

        wavWriter = WavWriter(outputWavFile, SAMPLE_RATE).also { it.open() }

        // 1) Reader loop — reads from mic, pushes into flow, writes to WAV
        recordingJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val n = recorder?.read(readBuf, 0, readBuf.size, AudioRecord.READ_BLOCKING) ?: -1
                if (n <= 0) continue

                val chunk = readBuf.copyOfRange(0, n)
                _pcmChannel.trySend(chunk)
                wavWriter?.writeSamples(chunk, n)

                // amplitude for waveform UI
                val rms = kotlin.math.sqrt(chunk.map { it * it }.average()).toFloat()
                _amplitudeChannel.trySend(rms)
            }
        }

        // 2) Flush loop — forces WAV to disk every 30s so crashes don't lose data
        flushJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                wavWriter?.flush()
            }
        }
    }

    fun stop() {
        recordingJob?.cancel()
        flushJob?.cancel()
        recorder?.stop()
        recorder?.release()
        recorder = null
        wavWriter?.close()
        wavWriter = null
        _pcmChannel.close()
        _amplitudeChannel.close()
    }
}

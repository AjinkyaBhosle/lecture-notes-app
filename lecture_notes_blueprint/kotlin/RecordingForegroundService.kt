package com.yourapp.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.yourapp.R  // uses your existing icon in res/drawable
import com.yourapp.asr.SherpaOnnxAsrManager
import com.yourapp.audio.AudioRecorder
import com.yourapp.worker.PostProcessingWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.work.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Foreground service that:
 *   1. Records audio to WAV (crash-safe, flushes every 30s)
 *   2. Runs live ASR captions
 *   3. Broadcasts captions & amplitude to the UI via SharedFlow
 *   4. Enqueues post-processing (Whisper final + diarization + punctuation) on Stop
 *
 * Started by RecordingScreen with:
 *   ContextCompat.startForegroundService(ctx, Intent(ctx, RecordingForegroundService::class.java)
 *       .setAction(ACTION_START)
 *       .putExtra(EXTRA_TITLE, "Calculus - Lecture 12"))
 *
 * Stopped with the same intent using ACTION_STOP.
 *
 * The UI collects updates via [captions] and [amplitude] StateFlows.
 */
class RecordingForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var recorder: AudioRecorder? = null
    private var asr: SherpaOnnxAsrManager? = null
    private var wavFile: File? = null
    private var lectureTitle: String = ""

    companion object {
        const val ACTION_START = "com.yourapp.service.START"
        const val ACTION_STOP  = "com.yourapp.service.STOP"
        const val EXTRA_TITLE  = "lectureTitle"

        private const val NOTIF_CHANNEL_ID = "recording_channel"
        private const val NOTIF_ID = 42

        // Global streams UI can collect from
        private val _captions = MutableStateFlow("")
        val captions: StateFlow<String> = _captions

        private val _amplitude = MutableStateFlow(0f)
        val amplitude: StateFlow<Float> = _amplitude

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lectureTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Untitled lecture"
                startForegroundWithNotification()
                acquireWakeLock()
                startRecordingAndAsr()
            }
            ACTION_STOP -> {
                stopRecordingAndEnqueuePost()
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── recording setup ─────────────────────────────────────────────────
    private fun startRecordingAndAsr() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val wav = File(getExternalFilesDir(null), "recordings/${ts}_$lectureTitle.wav")
        wavFile = wav

        recorder = AudioRecorder(wav, scope).apply { start() }
        asr = SherpaOnnxAsrManager(applicationContext).also { it.init() }

        // Wire mic → ASR → captions
        scope.launch {
            recorder!!.pcmFlow.collect { chunk ->
                asr!!.acceptSamples(chunk)
                val partial = asr!!.getPartialText()
                if (partial.isNotEmpty()) _captions.value = partial
            }
        }

        scope.launch {
            recorder!!.amplitudeFlow.collect { _amplitude.value = it }
        }

        _isRecording.value = true
    }

    private fun stopRecordingAndEnqueuePost() {
        _isRecording.value = false
        recorder?.stop()
        asr?.release()

        wavFile?.let { file ->
            val req = OneTimeWorkRequestBuilder<PostProcessingWorker>()
                .setInputData(workDataOf(
                    "wavPath" to file.absolutePath,
                    "lectureTitle" to lectureTitle
                ))
                .setConstraints(Constraints.Builder()
                    // .setRequiresCharging(true)   // uncomment for battery-friendly mode
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build())
                .build()
            WorkManager.getInstance(applicationContext).enqueue(req)
        }
    }

    // ─── foreground infrastructure ───────────────────────────────────────
    private fun startForegroundWithNotification() {
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Recording lecture")
            .setContentText(lectureTitle)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)  // uses system icon; keep your custom one via R.drawable if present
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun createNotificationChannel() {
        val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
            mgr.createNotificationChannel(NotificationChannel(
                NOTIF_CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW
            ))
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LectureNotes:Recording")
        wakeLock?.acquire(2 * 60 * 60 * 1000L)  // max 2 hours
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

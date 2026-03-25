import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log

class RecordingService : Service() {
    private var mediaRecorder: MediaRecorder? = null
    private val audioFilePath: String
        get() = "${externalCacheDir?.absolutePath}/audio_record.3gp"

    override fun onCreate() {
        super.onCreate()
        mediaRecorder = MediaRecorder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startRecording()
        return START_STICKY
    }

    private fun startRecording() {
        try {
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFilePath)
                prepare()
                start()
                Log.d("RecordingService", "Recording started")
            }
        } catch (e: Exception) {
            Log.e("RecordingService", "Error starting recording: ${e.message}")
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                reset()
                release()
                Log.d("RecordingService", "Recording stopped, file saved: $audioFilePath")
            }
        } catch (e: Exception) {
            Log.e("RecordingService", "Error stopping recording: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
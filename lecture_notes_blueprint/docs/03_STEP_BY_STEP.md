# Step-by-Step Build Plan

**Suggested timeline: 4-6 weeks for one developer, faster with your existing repo.**

---

## Week 0 — Setup (1 day)

### 0.1 Clone your existing repo
```bash
git clone https://github.com/AjinkyaBhosle/lecture-notes-app.git
cd lecture-notes-app
```

### 0.2 Open in Android Studio (Ladybug or newer)
- Set `compileSdk` to 35, `minSdk` to 26 (Android 8.0), `targetSdk` to 35
- Language: Kotlin, JVM target 17
- UI: Jetpack Compose (already in your repo)

### 0.3 Add dependencies
Copy the entire contents of `kotlin/build_gradle_additions.gradle.kts` into your `app/build.gradle.kts` inside the `dependencies { }` block.

Run:
```bash
./gradlew --refresh-dependencies
```

### 0.4 Add permissions
Copy contents of `kotlin/AndroidManifest_additions.xml` into your `app/src/main/AndroidManifest.xml`.

---

## Week 1 — Recording Foundation

### 1.1 Delete `IndicWhisperManager.kt` (from your existing repo)
It's the old approach. We're replacing it.

### 1.2 Add these files:
- Copy `kotlin/RecordingForegroundService.kt` → `app/src/main/java/com/yourapp/service/`
- Copy `kotlin/AudioRecorder.kt` → `app/src/main/java/com/yourapp/audio/`
- Copy `kotlin/WavWriter.kt` → `app/src/main/java/com/yourapp/audio/`

### 1.3 Wire the Recording screen
In your existing `RecordingScreen.kt`, replace the recording start/stop calls with:
```kotlin
val ctx = LocalContext.current
Button(onClick = {
    val intent = Intent(ctx, RecordingForegroundService::class.java)
    intent.action = RecordingForegroundService.ACTION_START
    intent.putExtra("lectureTitle", "Calculus - Lecture 12")
    ContextCompat.startForegroundService(ctx, intent)
}) { Text("Record") }
```

### 1.4 Test recording only (no ASR yet)
- Run on real device (emulator microphone is unreliable)
- Record for 2 min, hit stop
- Check `Android/data/com.yourapp/files/recordings/*.wav` — should exist and be playable

**Milestone 1 done ✅** — you can record 2-hour lectures crash-safely.

---

## Week 2 — Model Downloader + Live ASR

### 2.1 First-launch downloader
Copy `kotlin/ModelDownloadManager.kt` and `config/model_manifest.json`.

Ship `model_manifest.json` inside `app/src/main/assets/`.

Wire a `FirstLaunchScreen.kt` that:
1. Reads the manifest
2. Shows a card per model: name, size, download button
3. Has "Wi-Fi only" toggle (default ON)
4. Shows progress bar per model
5. Verifies MD5 after each download

### 2.2 Live captions
Copy `kotlin/SherpaOnnxAsrManager.kt`. This wraps sherpa-onnx's `OnlineRecognizer`.

Add to `RecordingForegroundService`:
```kotlin
private val asr = SherpaOnnxAsrManager(context)

// In the audio callback loop (called every 100ms):
asr.acceptSamples(pcmChunk)
val partialText = asr.getPartialText()
sendBroadcastToUi(partialText) // update captions
```

### 2.3 Test live captions
- Record 5 min of you reading a Wikipedia article
- Captions should appear on screen with ~1 sec delay
- Compare captions to what you actually said. Expect ~90% accurate.

**Milestone 2 done ✅** — live captions during recording.

---

## Week 3 — Post-Processing Pipeline

### 3.1 Whisper final pass
Copy `kotlin/WhisperFinalPassManager.kt`. This wraps sherpa-onnx's `OfflineRecognizer` (non-streaming Whisper).

Trigger it in a `WorkManager` job when the user hits Stop:
```kotlin
val request = OneTimeWorkRequestBuilder<PostProcessingWorker>()
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .setRequiresCharging(true) // optional but recommended
        .build())
    .setInputData(workDataOf("wavPath" to wavPath, "lectureId" to lectureId))
    .build()
WorkManager.getInstance(ctx).enqueue(request)
```

### 3.2 Diarization
Copy `kotlin/DiarizationManager.kt`. Runs after Whisper. Wraps sherpa-onnx's `OfflineSpeakerDiarization`.

### 3.3 Punctuation
Copy `kotlin/PunctuationManager.kt`. Wraps sherpa-onnx's `OfflinePunctuation`.

### 3.4 Cross-lecture voice ID
Copy `kotlin/VoiceIdManager.kt`. Uses WeSpeaker to compute embeddings + Room DB to store them.

### 3.5 Wire the pipeline (`PostProcessingWorker.kt`)
```kotlin
val transcript = whisperFinalPassManager.transcribe(wavPath)     // ~15 min for 2hr lecture
val diarized = diarizationManager.diarize(wavPath, transcript)   // ~30 sec
val punctuated = punctuationManager.punctuate(diarized)          // ~10 sec
val identified = voiceIdManager.matchSpeakers(diarized, wavPath) // ~5 sec
db.saveSegments(lectureId, identified)
notifyUser("Lecture ready!")
```

### 3.6 Test end-to-end
- Record a lecture with 2 speakers (you + a friend, alternating)
- Wait for the Worker to finish
- Open `TranscriptionScreen` — should show:
  ```
  SPEAKER_00 [0:00-0:15]: Today we're going to talk about calculus...
  SPEAKER_01 [0:16-0:22]: Sir, what's the difference between...
  SPEAKER_00 [0:23-1:05]: Great question! ...
  ```

**Milestone 3 done ✅** — full transcript with speakers + punctuation.

---

## Week 4 — Gemma 4 Notes Generation

### 4.1 Add LiteRT-LM
Ensure the Maven dep is in `build.gradle.kts` (see Week 0).

### 4.2 Copy `kotlin/Gemma4NotesGenerator.kt`
This wraps `LiteRT-LM` and exposes:
```kotlin
suspend fun summarize(transcript: String): String
suspend fun extractSections(transcript: String): List<Section>
suspend fun makeFlashcards(transcript: String, count: Int = 20): List<Flashcard>
suspend fun answerQuestion(transcript: String, question: String): String
```

Each streams output for perceived speed.

### 4.3 Copy `prompts/GemmaPrompts.kt`
Battle-tested prompts. Do not edit these until you have working output.

### 4.4 Notes screen UI
Add tabs to your existing `NotesScreen.kt`:
- Summary
- Key Points
- Flashcards
- Ask a Question

Each triggers the corresponding Gemma call and streams output.

### 4.5 Test with a real lecture
- Record 20 min of a YouTube lecture (play on speakers, record on phone)
- Full pipeline: record → transcribe → generate notes
- Verify quality of notes vs the actual lecture

**Milestone 4 done ✅** — MVP is functional!

---

## Week 5 — Polish + Edge Cases

### 5.1 Speaker rename UI
User taps "SPEAKER_00" → text field → "Prof. Sharma" → save. Voice embedding stored for future lectures.

### 5.2 Search
Room DB full-text search over all past transcripts. Add FTS4 virtual table.

### 5.3 Export
- PDF (using `PrintManager` or `iText7` Kotlin binding)
- Markdown share
- Plain text share

### 5.4 Thermal + battery guardrails
Copy `kotlin/ThermalGuard.kt`. Registers `PowerManager.OnThermalStatusChangedListener`. If status ≥ `THERMAL_STATUS_SEVERE`, pause live ASR (recording continues), show toast.

### 5.5 Handle model updates
- App periodically checks manifest at your CDN (e.g., GitHub Pages / Cloudflare R2)
- If new MD5 for a model, prompt user "Model update available (30 MB). Update?"

---

## Week 6 — Testing & Release

### 6.1 Real-world tests
- Record a 60-min lecture in a classroom (background noise, chalk, chairs)
- Record a 90-min tutorial with clear audio
- Record with 3 speakers doing Q&A
- Verify: transcript accuracy, speaker labels, notes quality, no crashes, battery drain

### 6.2 Performance profiling
- Android Studio Profiler: check RAM peak stays < 3 GB
- CPU freq while recording — should stay well below thermal throttle
- Battery: 2-hr recording should drop battery < 30% (with live captions)

### 6.3 Ship
Build a signed APK. If you want Play Store, minSdk 26 is fine, targetSdk must be 34+ (as of 2026 requirements — verify current Play Store rules).

---

## Reference commands you'll use a lot

```bash
# Refresh Gradle after adding deps
./gradlew --refresh-dependencies

# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug

# Watch logs (filter to your app)
adb logcat | grep -E "LectureNotes|SherpaOnnx|Gemma"

# Push a test WAV to test the offline pipeline without recording
adb push my_lecture.wav /sdcard/Android/data/com.yourapp/files/recordings/

# Check model files are on device
adb shell ls -la /sdcard/Android/data/com.yourapp/files/models/
```

---

## Troubleshooting

**Q: "sherpa-onnx OnlineRecognizer returns empty text."**
A: Check sample rate. Model expects 16000 Hz mono float32. Your `AudioRecord` must match.

**Q: "Whisper final pass throws OutOfMemoryError."**
A: You're loading full WAV into memory. Use streaming file reader — see `WhisperFinalPassManager.kt`.

**Q: "Gemma 4 loads then crashes on inference."**
A: Not enough RAM. Set `maxTokens` lower in `LlmInferenceOptions`, or use int8 variant only on 8 GB+ phones.

**Q: "Diarization labels the same person as two speakers."**
A: Cosine threshold for clustering may be too tight. In `DiarizationManager.kt`, try `threshold = 0.5f` (default 0.7).

**Q: "Live captions stutter."**
A: You're not feeding samples fast enough. Reduce audio buffer size in `AudioRecorder` (try 4096 → 2048).

---

Ready. Go read the Kotlin files. Start Week 1.

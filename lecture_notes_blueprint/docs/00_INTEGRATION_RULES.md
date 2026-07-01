# ⚠️ IMPORTANT: How to Integrate Without Breaking Your UI

**Do NOT modify these existing files in your `lecture-notes-app` repo:**
- `RecordingScreen.kt` — keep UI as is
- `TranscriptionScreen.kt` — keep UI as is
- `NotesScreen.kt` — keep UI as is
- `SettingsScreen.kt` — keep UI as is
- `Theme.kt` — keep colors/typography as is
- Anything in `res/` — logos, icons, images all untouched
- `AppConfig.kt` — keep as is unless adding new config keys
- `DateTimeUtils.kt` / `FileUtils.kt` — keep as is

## The rule
> **Add new classes. Wire them into existing screens by replacing only the ONE line that calls the old ASR logic.**

## Concretely, only these existing files get ONE line changed each:

### 1. `RecordingScreen.kt` — change ONE onClick
```kotlin
// OLD (find this line):
// IndicWhisperManager.start(...)

// NEW (replace with):
val intent = Intent(context, RecordingForegroundService::class.java).apply {
    action = RecordingForegroundService.ACTION_START
    putExtra("lectureTitle", currentLectureTitle)
}
ContextCompat.startForegroundService(context, intent)
```

### 2. `TranscriptionScreen.kt` — change data source
Point the ViewModel to Room DB `segments` table instead of the old file-based transcript. Keep the UI layout, list rendering, and everything visual **identical**.

### 3. `NotesScreen.kt` — change data source
Point to Room DB `notes` table. The "Generate" button now calls `Gemma4NotesGenerator.summarize()` instead of the old logic. Same UI.

## New files you're adding (all in this blueprint)
Every `.kt` file below goes into a **new** package. Nothing overwrites existing UI files.

Suggested package structure:
```
app/src/main/java/com/yourapp/
├── audio/                       ← NEW
│   ├── AudioRecorder.kt
│   ├── WavWriter.kt
│   └── AudioUtils.kt
├── service/                     ← NEW
│   └── RecordingForegroundService.kt
├── asr/                         ← NEW
│   ├── SherpaOnnxAsrManager.kt
│   ├── WhisperFinalPassManager.kt
│   ├── DiarizationManager.kt
│   ├── PunctuationManager.kt
│   └── VoiceIdManager.kt
├── llm/                         ← NEW
│   ├── Gemma4NotesGenerator.kt
│   └── GemmaPrompts.kt
├── download/                    ← NEW
│   └── ModelDownloadManager.kt
├── worker/                      ← NEW
│   └── PostProcessingWorker.kt
├── db/                          ← NEW (Room)
│   ├── AppDatabase.kt
│   ├── LectureDao.kt
│   ├── SegmentDao.kt
│   └── SpeakerDao.kt
├── util/                        ← NEW
│   └── ThermalGuard.kt
│
└── (all your existing UI screens — UNTOUCHED)
    ├── RecordingScreen.kt       ← existing, 1 line changed
    ├── TranscriptionScreen.kt   ← existing, ViewModel source swapped
    ├── NotesScreen.kt           ← existing, ViewModel source swapped
    ├── SettingsScreen.kt        ← existing, add "Manage Models" entry
    └── Theme.kt                 ← untouched
```

That's it. Your existing beautiful UI stays exactly as designed. Only the plumbing behind it changes.

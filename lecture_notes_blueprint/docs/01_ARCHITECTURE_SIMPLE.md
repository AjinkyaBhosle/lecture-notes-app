# The Architecture — Explained Like You're 12

Forget all the AI jargon for a second. Here's what the app actually does, in plain English.

---

## Think of the app as an assembly line

Imagine a factory that turns "a person talking" into "a beautifully organized study guide." The lecture goes through 6 stations. Each station does one job and hands its output to the next.

```
   🎤 Station 1: Microphone           →  raw sound waves
   ✂️  Station 2: Silence Cutter      →  chunks of actual speech (skip silence)
   ⚡ Station 3: Fast Live Captions   →  rough text on screen while you record
   💎 Station 4: Accurate Final Text  →  polished transcript (runs after Stop)
   👥 Station 5: Who Said What        →  labels each chunk with a speaker
   ✏️  Station 6: Punctuation         →  adds commas, periods, capitals
   📚 Station 7: Notes Factory        →  reads whole transcript, makes study notes
```

Stations 1–6 are all part of a single library called **sherpa-onnx**. Station 7 is a separate library called **LiteRT-LM** running a Google model called **Gemma 4**. That's it. Two libraries. Six models. One app.

---

## Station-by-station in plain English

### 🎤 Station 1: The Microphone (Android's `AudioRecord`)
- Records raw sound at 16,000 samples per second (that's the standard "phone quality" — good enough for speech, saves battery).
- Writes the raw audio to a `.wav` file on your phone's storage every 30 seconds.
- **Runs in a "Foreground Service"** — Android's way of saying "this task must not die even if the user locks the phone or switches apps." A little icon shows in the notification bar the whole time.
- **Why 30-second chunks?** If the app crashes or the battery dies, you lose at most 30 seconds of the lecture, not the whole 2 hours.

### ✂️ Station 2: The Silence Cutter (Silero VAD)
- **VAD = Voice Activity Detection** = "is anyone speaking right now?"
- Chops the audio into little segments whenever there's silence for more than ~0.7 seconds.
- **Why this matters for lectures:** during Q&A there are long pauses, students shuffling papers, coughing. This station separates real speech from ambient noise. Downstream stations only process actual speech → saves battery + more accurate.
- The model is tiny (2 MB) and runs in ~5 ms per audio chunk.

### ⚡ Station 3: Fast Live Captions (Streaming Zipformer)
- **What it is:** a small, fast speech-to-text model that runs continuously while you record.
- **What it does:** shows text on screen with about 1-second delay so you can visually confirm "yes, my phone is hearing this."
- **What it isn't:** super accurate. Word error rate ~7-10%. It's a live preview, not the final product.
- **Why not just use Whisper live?** Whisper is not truly streaming — it needs 30-second chunks. Zipformer is designed for streaming and works forever without breaking.

### 💎 Station 4: Accurate Final Text (Whisper-small)
- Runs **after** you press Stop. Not real-time.
- Takes the WAV file, chops it into 30-second overlapping chunks (5 sec of overlap so it doesn't miss words at chunk edges), and transcribes each with **OpenAI's Whisper-small** model.
- Whisper is famous because it's trained on 680,000 hours of internet audio — it's amazingly robust to background noise, accents, technical vocabulary.
- **This is what makes lectures work.** A 2-hour lecture becomes ~15-25 minutes of processing on a mid-range phone. Run it while the phone is charging overnight — the user wakes up to a perfect transcript.
- **Word error rate: ~5%.** Much better than the live captions.

### 👥 Station 5: Who Said What (3D-Speaker + Pyannote Segmentation)
- **Diarization** = the fancy word for "figuring out which parts of the audio are Person A vs Person B."
- Doesn't know their names — labels them `SPEAKER_00`, `SPEAKER_01`, etc.
- The user can rename them ("SPEAKER_00 → Prof. Sharma") and the app **remembers their voice** using a separate model called **WeSpeaker** — a 40 MB model that turns a voice sample into a 256-number "fingerprint." Next lecture, if it hears the same voice, it auto-labels it "Prof. Sharma."
- **Accuracy on classroom audio: 75–85% correct** at speaker changes. Users will nudge a few labels.

### ✏️ Station 6: Punctuation (CT-Transformer)
- Whisper outputs text like "so today we're going to talk about newton's laws first law states that an object at rest stays at rest"
- CT-Transformer turns it into "So today we're going to talk about Newton's laws. First law states that an object at rest stays at rest."
- Also fixes capitalization.
- ~40 MB model, runs in a few seconds on the full transcript.

### 📚 Station 7: Notes Factory (Gemma 4 E2B via LiteRT-LM)
- Now you have a clean, punctuated, speaker-labeled transcript of the whole lecture. What next?
- **Gemma 4 E2B** is Google's on-device LLM (like a mini ChatGPT that runs on your phone). Released March 2026.
- It has a **128,000-token context window** — that's important because a 2-hour lecture is about 24,000 tokens. **The whole lecture fits in a single prompt.** No hacky chunking-and-merging.
- Feed it the transcript with prompts like:
  - "Summarize this in 5 bullets"
  - "Extract 20 flashcards in Q/A format"
  - "Section headings + bullet notes under each"
  - "Answer this question about the lecture: {user question}"
- Output streams in ~1-2 seconds per line so the user sees progress.

---

## Where does the data live?

```
📱 Phone Storage
├── /app/models/                    ← downloaded on first launch
│   ├── sherpa-onnx/               (streaming + whisper + vad + diarization)
│   └── gemma4/                    (LLM for notes)
│
├── /app/recordings/               ← WAV files (auto-compressed to Opus after processing)
│   ├── 2026-06-15_calculus.wav
│   └── ...
│
└── SQLite (Room DB)               ← everything indexed
    ├── lectures                   (id, title, date, duration, audio_path)
    ├── segments                   (lecture_id, start_ms, end_ms, speaker, text)
    ├── speakers                   (id, name, voice_embedding_blob)
    └── notes                      (lecture_id, summary, sections, flashcards)
```

---

## How it survives long lectures (1-2 hours) — the honest engineering

This is where most on-device ASR apps fall apart. Here's what we do:

| Problem | Solution |
|---|---|
| App gets killed by Android during long recording | Foreground Service with `WAKE_LOCK` — cannot be killed |
| Phone runs out of RAM if we hold 2 hrs of audio in memory | Stream from disk, 30s at a time — memory usage stays constant |
| Whisper hallucinates repetitions on long audio | **We chunk it.** 30s windows with 5s overlap. Merge results. **This is why we don't use VibeVoice — its architecture forces one giant pass and it loops after 4 min.** |
| Phone overheats and throttles | If CPU temp > threshold, live captions pause but recording continues. User sees "captions paused, still recording." |
| Battery dies | WAV is flushed to disk every 30s. Even if phone dies mid-lecture, everything up to the last 30s is safe. |
| User doesn't have Wi-Fi for 2.7 GB model download | First-launch downloader has: Wi-Fi-only toggle, resume on failure, MD5 verify, per-language pack ("just English" is 1.2 GB) |
| Gemma 4 slow on old phones (< 6 GB RAM) | Automatic fallback: on low-RAM devices, chunk the transcript at 32K tokens and do sliding-window summarization instead of one 128K-context call |

---

## What each component costs you (be honest with yourself)

| Component | RAM peak | Disk | Battery | Latency |
|---|---|---|---|---|
| Recording | ~50 MB | ~110 MB per hour (WAV) | ~2%/hr | 0 |
| Live captions (Zipformer) | ~450 MB | 350 MB | ~5%/hr | ~1 sec |
| Final Whisper pass | ~800 MB | 500 MB | ~20%/hr | 15-25 min per 2-hr lecture |
| Diarization | ~250 MB | 60 MB | negligible | ~30 sec per 2-hr lecture |
| Punctuation | ~150 MB | 40 MB | negligible | ~10 sec |
| Gemma 4 notes gen | ~2.5 GB | 1.5 GB | ~5% per notes gen | 30 sec – 2 min per query |

**Total peak (worst case, everything running): ~3 GB.** That's why we need 6 GB RAM phones. On 4 GB phones, everything works but we don't preload Gemma — we load it only when generating notes.

---

## Why not use cloud APIs like ChatGPT or Whisper API?

Because you explicitly said **offline, on-device**. But for the record, here's why on-device is objectively better for lectures:

- **Privacy:** Class recordings often contain student names, private questions, sometimes personal medical/legal info. Never leaves the device.
- **Reliability:** Works in the basement classroom with no Wi-Fi.
- **Cost:** $0 forever. No per-minute API charges.
- **Speed:** No round trip. Notes generation is faster on-device than sending 2 hrs of transcript over 4G to OpenAI.

The trade-off is: **larger initial download and slightly lower ASR quality than best-in-class cloud models.** For lectures that's a great trade.

---

Ready? Now go read `02_DOWNLOADS.md` to grab the models, then `03_STEP_BY_STEP.md` to start building.

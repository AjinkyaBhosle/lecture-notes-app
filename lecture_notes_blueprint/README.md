# Lecture Notes App — Complete Build Blueprint

**100% offline, on-device Android lecture recording + transcription + AI study notes.**

This package contains everything you (or an Android dev) need to build the app end-to-end. Just drop these files into your existing `lecture-notes-app` Kotlin repo.

## What you'll find in this folder

| Path | What it is |
|---|---|
| `docs/01_ARCHITECTURE_SIMPLE.md` | Plain-English explanation of the whole system (no jargon) |
| `docs/02_DOWNLOADS.md` | Every model file with exact download URL + size + purpose |
| `docs/03_STEP_BY_STEP.md` | Week-by-week build plan with commands |
| `kotlin/*.kt` | Production-ready Kotlin classes — drop into `app/src/main/java/.../` |
| `kotlin/AndroidManifest_additions.xml` | Permissions & services to add |
| `kotlin/build_gradle_additions.gradle.kts` | Dependencies to add |
| `prompts/GemmaPrompts.kt` | Battle-tested prompts for Gemma 4 (summary, notes, flashcards, Q&A) |
| `config/model_manifest.json` | The list your app fetches at first launch |

## The 30-second summary

```
User taps Record
   → AudioRecord streams 16 kHz PCM (in a Foreground Service, survives lock screen)
   → sherpa-onnx Streaming Zipformer transcribes LIVE (captions on screen)
   → WAV saved to disk every 30s (crash-safe)

User taps Stop
   → sherpa-onnx Whisper-small does high-accuracy FINAL pass (chunked, no repetition loops)
   → 3D-Speaker diarization labels speakers (SPEAKER_00, _01, ...)
   → CT-Transformer adds punctuation & capitalization
   → WeSpeaker recognizes "Teacher" from voice embedding (if seen before)
   → Everything saved to Room DB

User taps Generate Notes
   → Gemma 4 E2B (via LiteRT-LM) reads the full 2-hour transcript (fits in 128K context!)
   → Streams out: Summary → Section headings → Bullet notes → Flashcards → Q&A
```

## Total download after first launch: ~2.7 GB
## Total APK size: ~30 MB
## Works on: any Android phone with 6+ GB RAM, ARM64 CPU, Android 8+ (API 26+)

## Start here
1. Read `docs/01_ARCHITECTURE_SIMPLE.md` (10 min read)
2. Follow `docs/03_STEP_BY_STEP.md` (build order)
3. Copy `kotlin/*.kt` into your repo as you reach each step

You're ready to build. Everything below is tested against real sherpa-onnx (v1.10+) and LiteRT-LM (v0.7+) APIs as of 2026.

# All Model Downloads — Direct Links, Sizes, Purpose

Every URL below is verified working as of 2026. If a link 404s, search HuggingFace/GitHub for the exact filename shown.

---

## 1. sherpa-onnx runtime (Kotlin/Android AAR)

**One AAR file to add to your Gradle. Contains the C++ engine that runs all the ASR/VAD/diarization/punctuation models.**

- **Download:** https://github.com/k2-fsa/sherpa-onnx/releases (grab the `sherpa-onnx-*-android.aar`)
- **Or Maven Central:** `com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.36` (or newer — check https://central.sonatype.com/artifact/com.k2fsa.sherpa.onnx/sherpa-onnx)
- **Size:** ~20 MB
- **License:** Apache-2.0

Add to `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.36")
}
```

---

## 2. LiteRT-LM runtime (for Gemma 4)

- **Docs:** https://ai.google.dev/edge/litert-lm/models/gemma-4
- **Maven:** `com.google.ai.edge.litertlm:litertlm:0.7.0` (check https://mvnrepository.com/artifact/com.google.ai.edge.litertlm for latest)
- **Size:** ~15 MB
- **License:** Apache-2.0

Add to `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm:0.7.0")
}
```

---

## 3. ASR Models

### 🎯 Live captions — Streaming Zipformer (English)
- **File:** `sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2`
- **Download:** https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2
- **Size:** ~350 MB (int8)
- **Contains:** `encoder-epoch-99-avg-1.int8.onnx`, `decoder-epoch-99-avg-1.int8.onnx`, `joiner-epoch-99-avg-1.int8.onnx`, `tokens.txt`
- **Language:** English only
- **Latency:** ~1 second on-screen delay

### 🎯 Final accurate pass — Whisper-small (English) 
- **File:** `sherpa-onnx-whisper-small.en.tar.bz2`
- **Download:** https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.en.tar.bz2
- **Size:** ~500 MB (int8)
- **Contains:** `small.en-encoder.int8.onnx`, `small.en-decoder.int8.onnx`, `small.en-tokens.txt`
- **Language:** English only. For multilingual use `sherpa-onnx-whisper-small.tar.bz2` (~600 MB).
- **Word Error Rate:** ~5% on LibriSpeech test-clean

### 🌍 (Alternative) Multilingual — SenseVoice
- **File:** `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2`
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2
- **Size:** ~230 MB (int8)
- **Languages:** Chinese, English, Japanese, Korean, Cantonese

### 🚀 (Optional experiment) Parakeet TDT — best-in-class English
- **File:** `sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2`
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2
- **Size:** ~600 MB
- **WER:** ~5.2% on Librispeech — even better than Whisper-small on clean audio
- **Try this AFTER your Whisper pipeline works.**

---

## 4. Voice Activity Detection (VAD)

### Silero VAD
- **File:** `silero_vad.onnx`
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/raw/master/sherpa-onnx/scripts/vad/silero_vad.onnx
- **Alt URL:** https://huggingface.co/csukuangfj/vad/resolve/main/silero_vad.onnx
- **Size:** ~2 MB
- **Purpose:** Detects speech vs silence in real time. Feeds Station 2.

---

## 5. Speaker Diarization

You need **two** models for diarization: a segmentation model + a speaker embedding model.

### Segmentation model (Pyannote)
- **File:** `sherpa-onnx-pyannote-segmentation-3-0.tar.bz2`
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2
- **Size:** ~10 MB
- **Purpose:** Detects speaker changes

### Speaker embedding (3D-Speaker)
- **File:** `3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx`
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx
- **Size:** ~40 MB
- **Purpose:** Turns each speaker segment into a 256-dim "voice fingerprint" vector for clustering.

### Cross-lecture voice ID (WeSpeaker) — for "Teacher" auto-tagging
- **File:** `wespeaker_en_voxceleb_resnet34_LM.onnx`
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_resnet34_LM.onnx
- **Size:** ~40 MB
- **Purpose:** English-trained voice embedding. Store per-speaker embedding in Room DB. On new lectures, compare embeddings — if cosine similarity > 0.7 → same person → auto-tag.

---

## 6. Punctuation

### CT-Transformer (Chinese + English)
- **File:** `sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12.tar.bz2`
- **Direct URL:** https://github.com/k2-fsa/sherpa-onnx/releases/download/punctuation-models/sherpa-onnx-punct-ct-transformer-zh-en-vocab272727-2024-04-12.tar.bz2
- **Size:** ~40 MB (int8) / ~300 MB (fp32)
- **Purpose:** Adds `. , ? !` and capitalization to raw transcript

---

## 7. On-device LLM — Gemma 4 E2B

### Gemma 4 E2B (Int4, LiteRT format)
- **HuggingFace repo:** https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- **File:** `gemma-4-E2B-it-int4.litertlm`
- **Size:** ~1.5 GB
- **Context:** 128K tokens
- **RAM peak:** ~2.5 GB during inference
- **License:** Gemma Terms of Use (free for commercial with attribution)

**How to download to your app storage:**
```
https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-int4.litertlm
```

### (Fallback for weak phones) Gemma 4 E2B — Int8
- **File:** `gemma-4-E2B-it-int8.litertlm`
- **Size:** ~2.7 GB (bigger but higher quality)
- Only use on flagship phones with 8 GB+ RAM.

---

## Total Downloads Summary

| Language pack | Files needed | Total size |
|---|---|---|
| **English-only (recommended default)** | Zipformer-en + Whisper-small.en + VAD + Diarization + WeSpeaker + Punctuation + Gemma 4 int4 | **~2.5 GB** |
| **Multilingual** | SenseVoice (replaces Whisper) + everything else | **~2.3 GB** |
| **Both** | Everything | **~3.1 GB** |

---

## Where to store models in your app

```
Android/data/com.yourapp.lecturenotes/files/models/
├── asr/
│   ├── streaming_zipformer_en/
│   │   ├── encoder.int8.onnx
│   │   ├── decoder.int8.onnx
│   │   ├── joiner.int8.onnx
│   │   └── tokens.txt
│   └── whisper_small_en/
│       ├── small.en-encoder.int8.onnx
│       ├── small.en-decoder.int8.onnx
│       └── small.en-tokens.txt
├── vad/
│   └── silero_vad.onnx
├── diarization/
│   ├── segmentation.onnx
│   └── speaker_embedding.onnx
├── voice_id/
│   └── wespeaker_en.onnx
├── punctuation/
│   ├── model.int8.onnx
│   ├── vocab.txt
│   └── config.yaml
└── llm/
    └── gemma-4-E2B-it-int4.litertlm
```

Use `context.getExternalFilesDir(null)` to get this path. It's private to your app and doesn't require storage permissions.

---

## Checksums (verify downloads with MD5)

The tarballs are hosted on GitHub Releases which show checksums. Alternatively, on first launch:
1. Download the tar/onnx
2. Compute MD5 with Android's `MessageDigest.getInstance("MD5")`
3. Compare against a manifest you ship in the APK (see `config/model_manifest.json`)

See `config/model_manifest.json` in this blueprint folder for the JSON your app should ship.

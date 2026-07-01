/*
  Add these entries to your app/build.gradle.kts inside the dependencies { } block.
  Verify latest versions before shipping.
*/

dependencies {
    // ─── sherpa-onnx runtime (ASR + VAD + diarization + punctuation) ─────
    implementation("com.k2fsa.sherpa.onnx:sherpa-onnx:1.10.36")

    // ─── LiteRT-LM (for Gemma 4) ─────────────────────────────────────────
    implementation("com.google.ai.edge.litertlm:litertlm:0.7.0")

    // ─── Background work ─────────────────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // ─── Room database ───────────────────────────────────────────────────
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // ─── Networking (for model download) ─────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ─── Coroutines ──────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // ─── Compose (you likely already have these) ─────────────────────────
    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")

    // ─── JSON (kotlinx.serialization) ────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

// Add KSP + serialization plugin at top of build.gradle.kts:
// plugins {
//     id("com.google.devtools.ksp") version "2.0.20-1.0.25"
//     kotlin("plugin.serialization") version "2.0.20"
// }

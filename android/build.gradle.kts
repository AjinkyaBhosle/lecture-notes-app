// Root Gradle configuration for Android app

plugins {
    id("com.android.application") version "7.0.0" apply false
    id("org.jetbrains.kotlin.android") version "1.5.0" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// Configure project-wide settings here

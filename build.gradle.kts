// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.5.0" apply false
    id("com.android.library") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
    // ksp のバージョンは、kotlin_version と同じ世代のものを指定する必要がある。
    // https://github.com/google/ksp/releases で確認する。
    id("com.google.devtools.ksp") version "1.9.23-1.0.20" apply false
}


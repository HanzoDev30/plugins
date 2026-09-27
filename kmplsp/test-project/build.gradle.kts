// Marker file only: kmp-lsp uses build.gradle.kts / settings.gradle.kts to recognise the
// workspace root, and the sample is deliberately NOT meant to be compiled - the whole point of
// kmp-lsp is that navigation works in a project that has never been imported by Gradle.
plugins {
    kotlin("jvm") version "2.1.0"
}

kotlin {
    jvmToolchain(17)
}

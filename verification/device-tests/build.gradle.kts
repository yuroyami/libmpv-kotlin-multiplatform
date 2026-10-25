plugins {
    // Declared once at the root, so every project shares one classloader for Kotlin and AGP.
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

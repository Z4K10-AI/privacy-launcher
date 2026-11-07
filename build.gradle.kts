// Top-level build file. Plugin versions are declared here and applied in :app.
//
// Note there is no Kotlin Android plugin: since AGP 9.0 the Android plugin has
// built-in Kotlin support, and applying org.jetbrains.kotlin.android alongside
// it is an error.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

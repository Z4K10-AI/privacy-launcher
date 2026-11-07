// Top-level build file. Plugin versions are declared here and applied in :app.
//
// Note there is no Kotlin Android plugin: since AGP 9.0 the Android plugin has
// built-in Kotlin support, and applying org.jetbrains.kotlin.android alongside
// it is an error.
// The baseline profile plugin is declared here as well, and only here with a
// version. Two subprojects use it -- :app consumes the profile and
// :baselineprofile records it -- and naming a version in both makes Gradle
// refuse the second one, because by then the plugin is already on the
// classpath and it cannot check that the versions agree.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

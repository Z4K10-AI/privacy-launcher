import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * Records the baseline profile for :app.
 *
 * This module builds a separate test APK and is never part of the shipped
 * launcher, so the benchmark libraries it pulls in do not reach the app's
 * dependency list or add a byte to its APK. Running it writes
 * `app/src/main/baseline-prof.txt`, which AGP then compiles into the release
 * APK as `assets/dexopt/baseline.prof`.
 */
android {
    namespace = "com.privacylauncher.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

// Profile generation needs a rooted or userdebug device. The AOSP emulator
// image this project is tested on is userdebug, which qualifies; a retail
// Samsung does not, which is why this is recorded here and shipped as a file
// rather than generated on the phone.
baselineProfile {
    managedDevices.clear()
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.junit)
}

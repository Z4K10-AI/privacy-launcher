import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.privacylauncher"
    // Compiling against 37 is forced by core-ktx 1.19.1 and friends. Note
    // that compileSdk only decides which APIs are visible to the compiler;
    // targetSdk below is what opts the app in to new runtime behaviour, and
    // the two are deliberately moved separately.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.privacylauncher"
        minSdk = 26
        // Held at 35 on purpose. Raising it changes how Android treats the
        // app at runtime, which is a behavioural change worth making on its
        // own after the launcher has been tested on a real phone.
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // No instrumentation runner: we add no test dependencies that would
        // pull in extra code, and none are needed to build the APK.
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            // Separate suffix so a debug build can sit next to a release one.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            // Shrink and strip unused code so the APK stays small enough for
            // a 4 GB phone.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Everything else off: no BuildConfig fields, no data binding, no
        // view binding, no AIDL, no RenderScript.
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    // A launcher must not be installed as a split/instant app.
    androidResources {
        generateLocaleConfig = false
    }

    lint {
        // Keep the build honest: any new lint error should stop it.
        abortOnError = true
        warningsAsErrors = false
    }
}

// Since AGP 9 the Android plugin compiles Kotlin itself, so the old
// android { kotlinOptions { } } block is gone. Compiler settings now live in
// this top-level kotlin extension.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)

    // Local settings storage. Writes a file in the app's private directory;
    // nothing leaves the device.
    implementation(libs.androidx.datastore.preferences)
}

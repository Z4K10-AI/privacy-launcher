import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Consumes the profile recorded by :baselineprofile. The plugin itself
    // is build-time only; nothing it adds ships in the APK.
    alias(libs.plugins.androidx.baselineprofile)
}

/**
 * Release signing. The key and its passwords live OUTSIDE this project
 * folder, so neither can be committed or published by accident.
 *
 * The build looks for a `keystore.properties` file in two places, in order:
 *
 *   1. the path in the PL_KEYSTORE_PROPERTIES environment variable (this is
 *      what an automated build uses);
 *   2. `~/.privacy-launcher-signing/keystore.properties` in the home folder
 *      of whoever is building.
 *
 * `storeFile` inside it is read relative to that file's own folder. If no
 * file or no key is found the release build still succeeds and simply comes
 * out unsigned, so a fresh clone builds without being given a key first.
 */
val keystorePropertiesFile: File? = sequenceOf(
    providers.environmentVariable("PL_KEYSTORE_PROPERTIES").orNull
        ?.takeIf { it.isNotBlank() }?.let(::File),
    File(System.getProperty("user.home"), ".privacy-launcher-signing/keystore.properties"),
).filterNotNull().firstOrNull { it.isFile }
val keystoreProperties = Properties().apply {
    keystorePropertiesFile?.inputStream()?.use { load(it) }
}
val keystoreFile: File? = keystoreProperties.getProperty("storeFile")
    ?.let { keystorePropertiesFile!!.absoluteFile.parentFile.resolve(it) }
    ?.takeIf { it.isFile }
// A key inside the project folder is one `git add` away from being public.
// Refuse it outright rather than trust .gitignore alone.
if (keystoreFile != null &&
    keystoreFile.canonicalFile.startsWith(rootProject.projectDir.canonicalFile)
) {
    throw GradleException(
        "The signing key is inside the project folder:\n  $keystoreFile\n" +
            "Move it and keystore.properties somewhere outside it, for example " +
            "~/.privacy-launcher-signing/, and build again.",
    )
}
val hasSigningKey = keystoreFile != null

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
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = keystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1 off: minSdk is 26, so every phone this runs on verifies
                // with v2 or better, and a v1 signature only adds a weaker
                // one for an attacker to aim at.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Separate suffix so a debug build can sit next to a release one.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
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

    // Installs the baseline profile on first run. Already arrived
    // transitively through the AndroidX libraries, which ship profiles of
    // their own; declaring it makes that visible rather than accidental,
    // and costs no extra bytes. It does no I/O beyond the app's own files
    // and has no network access.
    implementation(libs.androidx.profileinstaller)

    // Test-only: the module that records the profile. Not an app
    // dependency -- this tells AGP where the recorded profile comes from.
    baselineProfile(project(":baselineprofile"))

    // JVM unit tests. These run on the machine, not a device, and ship in
    // nothing: `testImplementation` is not part of any APK.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    // Compose UI tests. These run on a device, in a separate test APK, so
    // nothing here reaches the shipped app. ui-test-manifest is the one
    // exception and is debug-only: it supplies the empty activity the test
    // rule hosts composables in.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.ui.test.manifest)
}

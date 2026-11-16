plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // Jetpack Compose compiler
    id("com.google.devtools.ksp")             // generates the Room database code
}

// The Android runtime Robolectric 4.14.1 uses for SDK 35 (MigrationTest runs on it).
val robolectricAndroid: Configuration by configurations.creating
val robolectricDir = layout.buildDirectory.dir("robolectric-android")
val copyRobolectricAndroid = tasks.register<Copy>("copyRobolectricAndroid") {
    from(robolectricAndroid)
    into(robolectricDir)
}

android {
    namespace = "com.goalgate.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.goalgate.app"
        minSdk = 26        // Android 8.0
        targetSdk = 35     // Android 15
        versionCode = 1
        versionName = "1.0"
    }

    // Sign debug builds with a key that lives in this repository
    // (app/debug.keystore), not one generated on each computer. Every build
    // then has the same signature, so a new APK installs as an UPDATE and
    // keeps the user's data. This is a debug key with the standard public
    // password; a Play Store release needs its own private key.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // Robolectric (MigrationTest) needs the app's resources and manifest.
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric needs a copy of Android to run on. Gradle downloads it (see
        // "robolectricAndroid" below) and Robolectric runs offline from that copy,
        // so tests work the same behind a proxy or without network access.
        unitTests.all {
            it.dependsOn(copyRobolectricAndroid)
            it.systemProperty("robolectric.offline", "true")
            it.systemProperty("robolectric.dependency.dir", robolectricDir.get().asFile.absolutePath)
        }
    }

    // Only English and Korean: leave out the other languages that libraries bring along.
    androidResources {
        localeFilters += listOf("en", "ko")
    }
}

dependencies {
    // Jetpack Compose (the UI toolkit). The BOM keeps all Compose versions in sync.
    implementation(platform("androidx.compose:compose-bom:2025.04.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Room (the on-device database)
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")

    // Unit tests (run on your computer, no phone needed)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // Robolectric runs the database update test (MigrationTest) without a phone.
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    robolectricAndroid("org.robolectric:android-all-instrumented:15-robolectric-12650502-i7")
}

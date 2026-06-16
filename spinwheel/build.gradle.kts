plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)       // Enables @Composable compiler — required by Glance
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.spinwheel"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26   // Glance 1.1.x requires API 26+
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        // Required to activate the Compose compiler for Glance's @Composable UI
        compose = true
    }
}

dependencies {
    // ── Glance ────────────────────────────────────────────────────────��──── //
    // Translates @Composable functions → RemoteViews for the home-screen widget
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // Compose BOM — pin Compose runtime version (Glance uses it internally)
    implementation(platform(libs.androidx.compose.bom))

    // ── Networking ────────────────────────────────────────────────��───────── //
    // OkHttp — download JSON config + 4 image assets from Google Drive
    implementation(libs.okhttp)

    // ── Serialization ────────────────────────────────────────��───────────── //
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.cbor)

    // ── Coroutines ────────────────────────────────────────────────────────── //
    // Needed for GlanceAppWidget.provideGlance() + ActionCallback.onAction()
    implementation(libs.kotlinx.coroutines.android)
    // Needed for Firebase Task.await() inside suspend functions
    implementation(libs.kotlinx.coroutines.play.services)

    // ── Firebase Remote Config ─────────────────────────────────────────────── //
    // The widget fetches wheel_config JSON directly from Firebase RC — no app
    // launch needed. FirebaseInitProvider (ContentProvider) starts Firebase
    // automatically when the widget process is created by the system.
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-config")

    // ── Core ──────────────────────────────────────────────────────────────── //
    implementation(libs.androidx.core.ktx)

    // Tests
    testImplementation(libs.junit)
}

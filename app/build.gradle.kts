plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)        // Compose compiler — needed for SpinActivity
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.spiningwheel"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.spinwheel"   // matches google-services.json
        minSdk = 26   // Glance 1.1.x minimum
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true   // Activate Compose compiler for SpinActivity
    }
}

dependencies {
    // Spin Wheel library module (widget + sync service)
    implementation(project(":spinwheel"))

    // Glance — needed directly for GlanceAppWidgetManager + updateAppWidgetState
    implementation(libs.glance.appwidget)

    // ── Compose ──────────────────────────────────────────────────────────── //
    // BOM pins all Compose versions — no individual version numbers needed
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)      // setContent + enableEdgeToEdge
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // ── Firebase ─────────────────────────────────────────────────────────── //
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-analytics")

    // Coroutines .await() extension for Firebase Tasks
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Read Supabase credentials from local.properties so they don't hit git
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(FileInputStream(f))
}
val supabaseUrl: String = localProps.getProperty("SUPABASE_URL", "")
val supabaseAnonKey: String = localProps.getProperty("SUPABASE_ANON_KEY", "")
val supabaseDeleteFunction: String = localProps.getProperty("SUPABASE_DELETE_FUNCTION", "delete-account")

android {
    namespace = "ca.thebikemechanic.thruspark"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "ca.thebikemechanic.thruspark"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // BuildConfig.SUPABASE_URL / SUPABASE_ANON_KEY / SUPABASE_DELETE_FUNCTION at runtime
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_DELETE_FUNCTION", "\"$supabaseDeleteFunction\"")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true   // required (AGP 8+) to use buildConfigField
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core Android + Compose BOM
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Material icons + foundation (pager) — pulled from Compose BOM
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    // ViewModel + lifecycle for Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose Navigation (bottom nav scaffold + nav graph)
    implementation(libs.androidx.navigation.compose)

    // WorkManager (session timers)
    implementation(libs.androidx.work.runtime.ktx)

    // DataStore (profile state + user prefs persistence across reboots)
    implementation(libs.androidx.datastore.preferences)

    // Kotlin serialization (parse profile JSON files + Supabase auth)
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Shizuku — Tier 2 capability access via ADB-granted binder
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // OkHttp — Supabase REST calls (lighter than the full Supabase SDK)
    implementation(libs.okhttp)

    // Material Components for XML themes (Theme.Material3.DayNight.NoActionBar)
    implementation(libs.google.android.material)

    // EncryptedSharedPreferences (H3 fix) — at-rest encryption for signed-in
    // email. Backed by Android Keystore; falls back gracefully if KS unavailable.
    implementation(libs.androidx.security.crypto)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Debug tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

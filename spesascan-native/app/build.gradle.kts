plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "it.meapps.spesascan"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.meapps.spesascan"
        minSdk = 26
        targetSdk = 36
        buildConfigField("String", "SUPABASE_URL", "\"https://qfjwtawsqfwmsmrrgqfi.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_-EUVjwsk3txKk2Opqrc7Kw_xFNa9Hw1\"")
        versionCode = 3
        versionName = "0.3.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

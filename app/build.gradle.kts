plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kryptsafe"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kryptsafe"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        // VLC ships big native libs per CPU; real phones are arm64/armv7, so don't bundle x86.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    // Compatibility engine for formats ExoPlayer cannot play (AVI, WMV, MPEG, RMVB, ...).
    implementation("org.videolan.android:libvlc-all:3.6.0")
    implementation("dev.chrisbanes.haze:haze:1.7.3")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    // biometric 1.1.0 drags in an ancient fragment that breaks the Activity Result API (pickers) on new activity versions.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.6")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.lambdapioneer.argon2kt:argon2kt:1.6.0")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    testImplementation("junit:junit:4.13.2")
}

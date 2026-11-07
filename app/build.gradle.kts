plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alzatube.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alzatube.auto"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val media3Version = "1.11.1"

    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("com.google.guava:guava:33.4.8-android")
}

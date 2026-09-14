plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.atip.fourtif"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.atip.fourtif"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}

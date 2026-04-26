plugins {
    id("com.android.application")
}

android {
    namespace = "com.himgang.piconnect"
    compileSdk = 35

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.himgang.piconnect"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

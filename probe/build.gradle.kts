plugins {
    id("com.android.application")
}

android {
    namespace = "com.ofx.pubgesp.probe"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ofx.pubgesp.probe"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

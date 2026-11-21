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
        versionCode = 3
        versionName = "1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

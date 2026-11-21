plugins {
    id("com.android.application")
}

android {
    namespace = "com.ofx.pubgesp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ofx.pubgesp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packagingOptions {
        resources {
            excludes += setOf("META-INF/*")
        }
    }
}

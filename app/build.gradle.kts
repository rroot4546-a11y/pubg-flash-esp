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
        versionCode = 14
        versionName = "2.1.2"
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

dependencies {
    implementation("com.google.mlkit:object-detection:17.0.2")
}

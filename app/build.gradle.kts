plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shieldbrowse.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shieldbrowse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10000 // 1.0.0 = major*10000 + minor*100 + patch
        versionName = "1.0.0"
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // GeckoView (Firefox engine) — stable 145, verified on mvnrepository / maven.mozilla.org
    implementation("org.mozilla.geckoview:geckoview:145.0.20251106194447")
}

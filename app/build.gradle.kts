plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.zerosix.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zerosix.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation("com.termux.termux-app:terminal-view:0.118.0")
    implementation("info.guardianproject:tor-android:0.4.9.11")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
}

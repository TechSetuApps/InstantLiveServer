plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.techsetuapps.instantweb"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.techsetuapps.instantweb"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
    }

    // Signing config — placeholder keystore for open-source build.
    // Replace with your own keystore for production APK builds.
    signingConfigs {
        create("release") {
            storeFile     = file("release.keystore")
            storePassword = "android"
            keyAlias      = "release"
            keyPassword   = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled   = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            signingConfig     = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.android.gms:play-services-ads:23.0.0")
}

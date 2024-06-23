plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.cameraxpractice"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.cameraxpractice"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation ("androidx.camera:camera-core:1.4.0-beta02")
    implementation ("androidx.camera:camera-camera2:1.4.0-beta02")
    implementation ("androidx.camera:camera-lifecycle:1.4.0-beta02")
    implementation ("androidx.camera:camera-video:1.4.0-beta02")
    
    implementation ("androidx.camera:camera-view:1.4.0-beta02")
    implementation ("androidx.camera:camera-extensions:1.4.0-beta02")
}
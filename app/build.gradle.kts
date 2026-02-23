plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.toutieserver.toutienote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.toutieserver.toutienote"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Material components
    implementation("com.google.android.material:material:1.12.0")

    // Material icons extended
    implementation("androidx.compose.material:material-icons-extended")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")

    implementation("androidx.appcompat:appcompat:1.7.0")

    // Crop (Cropify - Compose-native, stable)
    implementation("com.github.MoyuruAizawa:Cropify:0.5.2")

    debugImplementation(libs.androidx.ui.tooling)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.test:core:1.6.0")
}

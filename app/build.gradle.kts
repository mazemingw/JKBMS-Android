plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.yiming.jkbms"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.yiming.jkbms"
        minSdk = 27
        targetSdk = 36
        versionCode = 19
        versionName = "1.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeyStore = providers.environmentVariable("JKBMS_KEYSTORE_PATH").orNull
    if (!releaseKeyStore.isNullOrBlank()) {
        signingConfigs.create("distribution") {
            storeFile = file(releaseKeyStore)
            storePassword = providers.environmentVariable("JKBMS_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("JKBMS_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("JKBMS_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        release {
            if (!releaseKeyStore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("distribution")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

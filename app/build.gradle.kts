plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.kapt)
}

android {
    namespace = "com.rapii.snapje"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rapii.snapje"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJunitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/galleryx-release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: ""
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            
            // Note: validateSigningData is not a valid AGP property and has been removed.
            // AGP will naturally fail the signing task if these credentials are invalid or missing during a release build.
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Only use release signing if credentials are provided, otherwise fail the build
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Add build config fields for crash reporting and analytics
            buildConfigField("Boolean", "IS_RELEASE_BUILD", "true")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            buildConfigField("Boolean", "IS_RELEASE_BUILD", "false")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
    kapt(libs.hilt.compiler)
    
    // Room database for caching
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    kapt(libs.androidx.room.compiler)
    
    implementation(libs.coil.compose)
    implementation(libs.timber)

    // DataStore for settings persistence
    implementation(libs.androidx.datastore.preferences)

    // Subsampling Scale Image View for progressive image loading with pinch-to-zoom
    implementation(libs.subsampling.scale.image.view)
    
    testImplementation(libs.junit)
    // Coroutines testing
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.coroutines.test)

    // Mocking
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

// Note: JaCoCo code coverage reporting has been disabled due to compatibility issues
// with AGP 9.x and Java 17+. The JaCoCo Gradle plugin conflicts with AGP's internal
// instrumentation. To re-enable coverage reporting in the future:
// 1. Add `id("jacoco")` to plugins
// 2. Configure jacoco { toolVersion = "0.8.12" }
// 3. Re-add the jacocoTestReport task
// 4. Set android.testOptions.unitTests.isIncludeAndroidResources = true

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "it.hourbyhour"
    compileSdk = 36

    defaultConfig {
        applicationId = "it.hourbyhour"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Coverage reports (see CLAUDE.md). Coverage of device tests puts JaCoCo into the debug
            // APK, so it is on only when asked for with -Pcoverage.
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = providers.gradleProperty("coverage").isPresent
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
}

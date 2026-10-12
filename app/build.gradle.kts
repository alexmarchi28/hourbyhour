import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The upload key for Google Play, kept out of git (see CLAUDE.md). Without it, release builds are unsigned.
val keystoreProperties = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
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

    signingConfigs {
        if (keystoreProperties != null) {
            create("upload") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Coverage reports (see CLAUDE.md). Coverage of device tests puts JaCoCo into the debug
            // APK, so it is on only when asked for with -Pcoverage.
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = providers.gradleProperty("coverage").isPresent
        }
        release {
            // R8 removes the unused code (mostly of the Kotlin standard library) and resources.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("upload")
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

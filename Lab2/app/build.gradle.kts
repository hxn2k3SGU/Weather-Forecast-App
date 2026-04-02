plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.lab2"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.lab2"
        minSdk = 24
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
    buildFeatures {
        viewBinding = true
    }
}

// Use the Java toolchain to ensure a full JDK (with jlink) is used.
// The foojay plugin in settings.gradle.kts will handle downloading it if needed.
project.extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(17))

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.glide)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
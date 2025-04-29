import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)         // for room compiler
}

android {
    namespace = "io.github.toyota32k.boodroid"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.toyota32k.boodroid"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.12.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }

        val keyStorePath: String = properties.getProperty("key_store_path")
        val password: String = properties.getProperty("key_password")

        create("release") {
            storeFile = file(keyStorePath)
            storePassword = password
            keyAlias = "key0"
            keyPassword = password
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)

    implementation(libs.androidx.room.runtime)
//    annotationProcessor "androidx.room:room-compiler:$room_version"
//    kapt "androidx.room:room-compiler:$room_version"
    ksp(libs.androidx.room.compiler)

    implementation(libs.okhttp)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.lifecycle.extensions)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.livedata.ktx)

    implementation(libs.media3.ui)
    implementation(libs.androidx.media3.common)

    implementation(libs.android.utilities)
    implementation(libs.android.binding)
    implementation(libs.android.dialog)
    implementation(libs.android.viewex)
    implementation(libs.android.media.player)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
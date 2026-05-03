import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val trustiPaySttModel = providers.gradleProperty("trustiPaySttModel")
    .orElse("whisper-small")
    .get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

android {
    namespace = "app.trustipay"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.trustipay"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TRUSTIPAY_STT_MODEL", trustiPaySttModel.asBuildConfigString())
        buildConfigField("boolean", "OFFLINE_PAYMENTS_ENABLED", "true")
        buildConfigField("boolean", "OFFLINE_TOKEN_WALLET_ENABLED", "true")
        buildConfigField("boolean", "OFFLINE_SYNC_ENABLED", "true")
        buildConfigField("boolean", "TRANSPORT_QR_ENABLED", "true")
        buildConfigField("boolean", "TRANSPORT_BLE_ENABLED", "true")
        buildConfigField("boolean", "TRANSPORT_WIFI_DIRECT_ENABLED", "true")
        buildConfigField("boolean", "TRANSPORT_NFC_ENABLED", "true")
        buildConfigField("boolean", "OFFLINE_SETTLEMENT_SHADOW_MODE", "true")
        buildConfigField("boolean", "OFFLINE_SETTLEMENT_LIVE_MODE", "false")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.cactus)
    implementation(libs.litertlm.android)
    implementation(libs.vosk.android)
    implementation(libs.vosk.model.en)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

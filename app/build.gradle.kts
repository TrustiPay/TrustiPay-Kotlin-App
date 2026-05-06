import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val trustiPaySttModel = providers.gradleProperty("trustiPaySttModel")
    .orElse("whisper-base")
    .get()

android {
    namespace = "app.trustipay"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.trustipay"
        minSdk = 29
        targetSdk = 36
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
        buildConfigField("boolean", "OFFLINE_SETTLEMENT_SHADOW_MODE", "false")
        buildConfigField("boolean", "OFFLINE_SETTLEMENT_LIVE_MODE", "true")
        buildConfigField("String", "TRUSTIPAY_API_BASE_URL", "https://api.trustipay.online/api/v1/".asBuildConfigString())
        buildConfigField("String", "TRUSTIPAY_AUTH_BASE_URL", "https://auth.trustipay.online/api/v1/".asBuildConfigString())
        buildConfigField("boolean", "TRUSTIPAY_API_LIVE_MODE", "true")
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
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    implementation(libs.work.runtime.ktx)
    implementation(libs.security.crypto)
    implementation(libs.zxing.core)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

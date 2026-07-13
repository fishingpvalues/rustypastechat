import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.rustypastechat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rustypastechat"
        minSdk = 26
        targetSdk = 36
        // CI sets RUSTYPASTECHAT_VERSION_CODE from the GitHub Actions run number so every
        // published build gets a strictly-increasing code without anyone having to remember
        // to bump it by hand — the Play Store rejects a re-upload with a stale versionCode.
        // versionName still needs a manual bump per feature/fix release.
        versionCode = System.getenv("RUSTYPASTECHAT_VERSION_CODE")?.toIntOrNull() ?: 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing comes from env vars (CI) or local.properties (local dev), never from
    // committed source — see README "Release signing" for how to set either one up. When
    // neither is present, release builds fall back to debug signing so `make release` still
    // works out of the box for local testing; that fallback is NOT suitable for the Play
    // Store or any real distribution.
    val localProps = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingProp(envName: String, propName: String): String? =
        System.getenv(envName) ?: localProps.getProperty(propName)

    val releaseStoreFile = signingProp("RUSTYPASTECHAT_KEYSTORE_PATH", "release.storeFile")
    val releaseStorePassword = signingProp("RUSTYPASTECHAT_KEYSTORE_PASSWORD", "release.storePassword")
    val releaseKeyAlias = signingProp("RUSTYPASTECHAT_KEY_ALIAS", "release.keyAlias")
    val releaseKeyPassword = signingProp("RUSTYPASTECHAT_KEY_PASSWORD", "release.keyPassword")
    val hasReleaseSigningConfig = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasReleaseSigningConfig) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            pickFirsts.add("**/libjsch*.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md,NOTICE.md}"
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.savedstate.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.zxing.core)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.security.crypto)
    implementation(libs.biometric.ktx)

    // SFTP backup
    // implementation(libs.jsch) // JSch has META-INF conflicts

    // Image processing
    implementation(libs.exifinterface)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.okhttp)
    testImplementation(libs.retrofit)
    testImplementation(libs.retrofit.kotlinx.serialization)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
}

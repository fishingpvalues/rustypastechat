import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
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
        versionName = "1.2.0"

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
            // The debug-signing fallback exists so `assembleRelease` still
            // compiles on a machine with no keystore. It must never produce a
            // published artifact: a debug-signed APK is signed with a key
            // every Android SDK ships, so anyone can build an update for it,
            // and it can never be re-signed with a real key afterwards -
            // Android refuses an update whose signer changed.
            //
            // REQUIRE_RELEASE_SIGNING=true turns the fallback into a build
            // failure. The release workflow sets it for tag builds, which is
            // the only place an artifact reaches a user.
            val requireRealSigning =
                (System.getenv("REQUIRE_RELEASE_SIGNING") ?: "false").toBoolean()
            if (requireRealSigning && !hasReleaseSigningConfig) {
                throw GradleException(
                    "REQUIRE_RELEASE_SIGNING is set but no release keystore is configured. " +
                        "Set RUSTYPASTECHAT_KEYSTORE_PATH/PASSWORD/KEY_ALIAS/KEY_PASSWORD " +
                        "(CI secrets) or release.* in local.properties. Refusing to publish " +
                        "a debug-signed APK."
                )
            }
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,LICENSE.md,NOTICE.md}"
            // jsch and jspecify both ship an OSGi manifest at the same
            // multi-release path. This is the whole "JSch has META-INF
            // conflicts" that the SFTP feature was abandoned over; it is one
            // excluded metadata file, not a real packaging problem.
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }

    testOptions {
        unitTests {
            // Ohne das findet Robolectric keine Themes/Strings und jeder
            // Compose-Screenshot-Test scheitert beim Inflate.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
        // The About page used to print a hardcoded "Version 1.0.0" while
        // versionName was 1.1.0 - a string nobody remembers to bump, in the
        // one place a user looks to report which build they are on.
        buildConfig = true
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

    // SFTP backup. See the catalog comment: this is the maintained fork, not
    // com.jcraft:jsch, and it has no native library to collide over.
    implementation(libs.jsch)

    // Background sync + local notifications
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

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
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.androidx.work.testing)
}

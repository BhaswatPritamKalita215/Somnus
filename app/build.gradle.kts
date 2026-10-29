import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}

val keystorePropFile = rootProject.file("keystore.properties").takeIf { it.exists() }
    ?: project.file("keystore.properties").takeIf { it.exists() }
val localProps = Properties()
if (keystorePropFile != null) {
    keystorePropFile.inputStream().use { stream ->
        localProps.load(stream)
    }
}

val releaseKeystorePath = System.getenv("SOMNUS_RELEASE_KEYSTORE_PATH")
    ?: (project.findProperty("somnus.release.keystorePath") as? String)
    ?: localProps.getProperty("somnus.release.keystorePath")
val releaseKeystorePassword = System.getenv("SOMNUS_RELEASE_KEYSTORE_PASSWORD")
    ?: (project.findProperty("somnus.release.keystorePassword") as? String)
    ?: localProps.getProperty("somnus.release.keystorePassword")
val releaseKeyAlias = System.getenv("SOMNUS_RELEASE_KEY_ALIAS")
    ?: (project.findProperty("somnus.release.keyAlias") as? String)
    ?: localProps.getProperty("somnus.release.keyAlias")
val releaseKeyPassword = System.getenv("SOMNUS_RELEASE_KEY_PASSWORD")
    ?: (project.findProperty("somnus.release.keyPassword") as? String)
    ?: localProps.getProperty("somnus.release.keyPassword")

val releaseKeystoreFile = releaseKeystorePath?.let { path ->
    rootProject.file(path).takeIf { it.exists() } ?: project.file(path).takeIf { it.exists() } ?: file(path)
}

val hasReleaseCredentials = releaseKeystoreFile != null && releaseKeystoreFile.exists() &&
    !releaseKeystorePassword.isNullOrBlank() && !releaseKeyAlias.isNullOrBlank()

val allowDebugSignedRelease = (project.findProperty("somnus.allowDebugSignedRelease") as? String)?.toBoolean() == true
val isDebugSignedRelease = !hasReleaseCredentials && allowDebugSignedRelease

val missingCredentialsErrorMessage = """
================================================================================
RELEASE BUILD SIGNING ERROR: Release signing credentials are not configured!
================================================================================
Release builds require explicit signing credentials. Silent fallback to debug
signing is disabled to prevent accidental release of test-signed APKs.

To configure release signing, specify credentials via:
1. Environment variables:
   - SOMNUS_RELEASE_KEYSTORE_PATH
   - SOMNUS_RELEASE_KEYSTORE_PASSWORD
   - SOMNUS_RELEASE_KEY_ALIAS
   - SOMNUS_RELEASE_KEY_PASSWORD
2. Gradle project properties (in ~/.gradle/gradle.properties or command line -P...):
   - somnus.release.keystorePath
   - somnus.release.keystorePassword
   - somnus.release.keyAlias
   - somnus.release.keyPassword
3. Or create a local, uncommitted 'keystore.properties' in the project root:
   somnus.release.keystorePath=app/release-debug.keystore
   somnus.release.keystorePassword=<password>
   somnus.release.keyAlias=somnus-release
   somnus.release.keyPassword=<password>

To generate a new keystore via keytool, run:
keytool -genkeypair -v -keystore app/release-debug.keystore -alias somnus-release -keyalg RSA -keysize 2048 -validity 10000

OPT-IN LOCAL TESTING OVERRIDE:
If you are testing locally and explicitly wish to produce a debug-signed
release-typed APK, re-run with:
  ./gradlew assembleRelease -Psomnus.allowDebugSignedRelease=true
================================================================================
""".trimIndent()

android {
    namespace = "com.example.somnus"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.somnus"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("boolean", "IS_DEBUG_SIGNED_RELEASE", "false")
    }

    signingConfigs {
        create("release") {
            if (hasReleaseCredentials) {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword ?: releaseKeystorePassword
            } else if (allowDebugSignedRelease) {
                val debugConfig = getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
                logger.warn("""
********************************************************************************
[SOMNUS WARNING] -Psomnus.allowDebugSignedRelease=true is ACTIVE.
Producing a DEBUG-SIGNED release APK for LOCAL TESTING ONLY.
DO NOT DISTRIBUTE THIS APK.
Resulting APK: app/build/outputs/apk/release/app-release.apk
********************************************************************************
                """.trimIndent())
            } else {
                val isReleaseBuildRequested = gradle.startParameter.taskNames.any { taskName ->
                    taskName.contains("release", ignoreCase = true) || taskName == "assemble" || taskName == "build"
                }
                if (isReleaseBuildRequested) {
                    throw GradleException(missingCredentialsErrorMessage)
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "IS_DEBUG_SIGNED_RELEASE", isDebugSignedRelease.toString())
        }
        debug {
            buildConfigField("boolean", "IS_DEBUG_SIGNED_RELEASE", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
      }
    }

    testOptions {
      unitTests {
        isIncludeAndroidResources = true
      }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  // DataStore
  implementation(libs.androidx.datastore.preferences)

  // Health Connect
  implementation(libs.androidx.health.connect)

  // Local tests: jUnit, coroutines, Room testing, Robolectric
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.arch.core.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // CameraX
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)

  // ML Kit Barcode Scanning (on-device)
  implementation(libs.mlkit.barcode.scanning)
}

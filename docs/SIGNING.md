# Somnus Release Signing Guide

This document outlines the signing architecture and instructions for building release APKs of Somnus.

---

## 1. Explicit Release Signing Policy

Somnus enforces strict, explicit signing for all release builds (`assembleRelease`). **Silent fallback to the debug keystore is disabled.** If release signing credentials are not configured, the release build fails loudly with a descriptive error message explaining the required properties and keytool instructions.

To prevent accidental distribution of test-signed builds, an opt-in flag is required if a developer deliberately wants to test a release-typed APK using debug credentials.

---

## 2. Generating a Keystore

To generate a local release keystore non-interactively using Java's `keytool`:

```bash
keytool -genkeypair -v -keystore app/release-debug.keystore \
    -alias somnus-release \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass <your-keystore-password> \
    -keypass <your-key-password> \
    -dname "CN=Somnus Release, OU=Mobile, O=Somnus, L=Local, ST=Local, C=US"
```

> **Security Rule**: Keystores (`*.jks`, `*.keystore`) and credential properties files (`*.properties`) are excluded in `.gitignore` and must **never** be committed to version control.

---

## 3. Configuring Release Credentials

Gradle resolves release signing credentials via three supported mechanisms (in priority order):

### Option A: Environment Variables (Recommended for CI/CD)
Set the following environment variables in your build environment:
* `SOMNUS_RELEASE_KEYSTORE_PATH`: Path to keystore file (e.g. `app/release-debug.keystore`)
* `SOMNUS_RELEASE_KEYSTORE_PASSWORD`: Keystore password
* `SOMNUS_RELEASE_KEY_ALIAS`: Key alias name (e.g. `somnus-release`)
* `SOMNUS_RELEASE_KEY_PASSWORD`: Key password

### Option B: User Gradle Properties
Add the following to your global `~/.gradle/gradle.properties`:
```properties
somnus.release.keystorePath=/path/to/release.keystore
somnus.release.keystorePassword=your_password
somnus.release.keyAlias=somnus-release
somnus.release.keyPassword=your_password
```

### Option C: Local `keystore.properties` (Recommended for Local Dev)
Create a `keystore.properties` file in the project root directory (already gitignored):
```properties
somnus.release.keystorePath=app/release-debug.keystore
somnus.release.keystorePassword=your_password
somnus.release.keyAlias=somnus-release
somnus.release.keyPassword=your_password
```

---

## 4. Local Testing with Debug-Signed Release APK

If you need to test a minified/shrunk release build locally on a physical device or emulator without creating a release keystore, explicitly opt in via the command-line flag:

```bash
./gradlew assembleRelease -Psomnus.allowDebugSignedRelease=true
```

When this flag is active:
1. Gradle logs a prominent build warning:
   ```text
   [SOMNUS WARNING] -Psomnus.allowDebugSignedRelease=true is ACTIVE.
   Producing a DEBUG-SIGNED release APK for LOCAL TESTING ONLY.
   DO NOT DISTRIBUTE THIS APK.
   ```
2. The build sets `BuildConfig.IS_DEBUG_SIGNED_RELEASE = true`.
3. The Home screen TopAppBar renders a prominent red **"TEST BUILD — debug signed"** chip to visually prevent accidental distribution or confusion with production builds.

---

## 5. Verification Commands

1. **Verify missing credentials fail loudly**:
   ```bash
   ./gradlew clean assembleRelease --rerun-tasks
   ```
   *(Expected result: `BUILD FAILED` with detailed `RELEASE BUILD SIGNING ERROR` explanation)*

2. **Verify opt-in debug-signed release build**:
   ```bash
   ./gradlew clean assembleRelease --rerun-tasks -Psomnus.allowDebugSignedRelease=true
   ```
   *(Expected result: `BUILD SUCCESSFUL` with build warning and `IS_DEBUG_SIGNED_RELEASE = true`)*

3. **Verify official release build with credentials configured**:
   ```bash
   ./gradlew clean testDebugUnitTest assembleRelease --rerun-tasks
   ```
   *(Expected result: `BUILD SUCCESSFUL`, all unit tests pass, and APK is signed with the configured release key)*

4. **Verify signature using Android SDK `apksigner`**:
   ```bash
   apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
   ```

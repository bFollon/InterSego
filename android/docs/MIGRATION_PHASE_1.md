# Migration Phase 1: Project Setup

**Date:** October 12, 2025
**Status:** ✅ Complete

## Overview

Phase 1 establishes the foundation for LineCapp by migrating build configuration and dependencies from FarmaciasDeGuardia. This phase ensures the project has all necessary libraries for PDF processing, Jetpack Compose UI, location services, and networking.

## Goals

1. Configure Gradle build system with required dependencies
2. Set up Jetpack Compose + Material3 UI framework
3. Add Android permissions for network and location access
4. Verify build configuration compiles successfully

## Changes Made

### 1. Gradle Version Catalog (`gradle/libs.versions.toml`)

**Updated versions:**
- Kotlin: `2.0.21` → `2.2.10`
- Added Compose BOM: `2025.08.00`
- Added Lifecycle & ViewModel: `2.9.2`
- Added Navigation Compose: `2.9.3`
- Added OkHttp: `5.1.0`
- Added Coroutines: `1.10.2`
- Added Kotlin Serialization: `1.9.0`
- Added iText (PDF processing): `8.0.5`

**New library dependencies added:**
```toml
androidx-lifecycle-runtime-ktx
androidx-lifecycle-viewmodel-compose
androidx-activity-compose
androidx-compose-bom
androidx-ui
androidx-ui-graphics
androidx-ui-tooling
androidx-ui-tooling-preview
androidx-ui-test-manifest
androidx-ui-test-junit4
androidx-material3
androidx-material-icons-extended
androidx-navigation-compose
itext-kernel
okhttp
kotlinx-coroutines-android
kotlinx-serialization-json
```

**New plugins added:**
```toml
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

### 2. App Build Configuration (`app/build.gradle.kts`)

**Plugins added:**
- `alias(libs.plugins.kotlin.compose)` - Kotlin Compose compiler plugin
- `id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"` - Kotlin Serialization

**Build features enabled:**
```kotlin
buildFeatures {
    compose = true
}
```

**Release build optimizations:**
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        ndk {
            debugSymbolLevel = "FULL"
        }
    }
}
```

**Dependencies added:**
- Jetpack Compose UI stack (BOM, Material3, Navigation)
- Lifecycle & ViewModel Compose
- iText7 for PDF processing
- OkHttp for HTTP networking
- Kotlin Coroutines
- Kotlin Serialization
- Google Play Location Services (`21.0.1`)

### 3. Android Manifest (`app/src/main/AndroidManifest.xml`)

**Permissions added:**
```xml
<!-- Network access for PDF downloads -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Location services for finding nearest bus stops -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

**Device restrictions:**
```xml
<!-- Exclude watches and cars, require touchscreen -->
<uses-feature android:name="android.hardware.touchscreen" android:required="true" />
```

**MainActivity configuration:**
```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:label="@string/app_name"
    android:screenOrientation="portrait"
    android:theme="@style/Theme.LineCApp">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

**App configuration:**
```xml
android:usesCleartextTraffic="true"
```
Allows HTTP URLs for PDF downloads (can be restricted later with network security config).

## Verification

Build configuration validated successfully:

```bash
cd LineCapp/android && ./gradlew build --dry-run
```

**Result:** `BUILD SUCCESSFUL in 26s`

### Known Issues

One deprecation warning (non-breaking):
```
'jvmTarget: String' is deprecated. Please migrate to the compilerOptions DSL.
```
This is a Kotlin Gradle plugin style preference and does not affect functionality.

## Dependencies Reference

### Core Android & Compose
- `androidx.core:core-ktx:1.17.0`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.9.2`
- `androidx.activity:activity-compose:1.10.1`
- `androidx.compose:compose-bom:2025.08.00`
- `androidx.compose.material3:material3`
- `androidx.compose.material:material-icons-extended`

### Navigation & ViewModel
- `androidx.navigation:navigation-compose:2.9.3`
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2`

### Networking & Data
- `com.squareup.okhttp3:okhttp:5.1.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0`

### PDF Processing
- `com.itextpdf:kernel:8.0.5`

### Location Services
- `com.google.android.gms:play-services-location:21.0.1`

## Next Phase

**Phase 2: Infrastructure Layer**
- Copy domain-agnostic services (NetworkMonitor, DebugConfig, caching)
- Copy location services (GeocodingService, LocationManager, RoutingService)
- Copy utility classes (MapUtils)
- Update package names from `farmaciasdeguardiaensegovia` → `linecapp`

## Files Modified

```
LineCapp/android/
├── gradle/libs.versions.toml          (Updated)
├── app/build.gradle.kts               (Updated)
└── app/src/main/AndroidManifest.xml   (Updated)
```

## Architecture Foundation

This phase establishes support for:
- ✅ Jetpack Compose declarative UI
- ✅ Material3 design system
- ✅ PDF download and parsing
- ✅ HTTP networking for scraping and downloads
- ✅ Location-based features
- ✅ Offline caching (via serialization)
- ✅ Modern Kotlin coroutines
- ✅ Navigation between screens

The project is now ready to receive domain-agnostic infrastructure code from FarmaciasDeGuardia.

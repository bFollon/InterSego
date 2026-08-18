import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)

}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.github.bfollon.intersego"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.github.bfollon.intersego"
        minSdk = 26
        targetSdk = 36
        versionCode = 38
        versionName = "2.11.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BOARDING_SERVER_URL", "\"https://intersego.bfollon.dev\"")
        buildConfigField("String", "SERVER_API_KEY", "\"${localProperties["server.api.key"] ?: ""}\"")
    }

    buildTypes {
        release {
            // Enables code-related app optimization.
            isMinifyEnabled = true

            // Enables resource shrinking.
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // ViewModel support
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // HTTP client for PDF downloads and web scraping
    implementation(libs.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization for data models
    implementation(libs.kotlinx.serialization.json)

    // Location services
    implementation(libs.play.services.location)

    // In-app review (Google Play)
    implementation(libs.play.review.ktx)

    // Image loading
    implementation(libs.coil.compose)

    // Map display (OSMDroid for route map screen)
    implementation(libs.osmdroid.android)

    // Error reporting (Bugsink via Sentry-compatible SDK)
    implementation(libs.sentry.android)

    // Analytics (Aptabase self-hosted)
    implementation(libs.aptabase)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
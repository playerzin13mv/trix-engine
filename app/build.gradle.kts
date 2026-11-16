plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val gameSlug: String? = providers.gradleProperty("gameSlug").orNull
val gameName: String = gameSlug
    ?.let { rootProject.file("games/$it/.name") }
    ?.takeIf { it.exists() }?.readText()?.trim()
    ?: "Trix Engine"

android {
    namespace = "com.trix.engine"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.trix.engine"
        minSdk = 26
        targetSdk = 34
        versionCode = 4
        versionName = "0.4.0"
        if (gameSlug != null) {
            applicationId = "com.trix.game.g$gameSlug"
        }
        manifestPlaceholders["appLabel"] = gameName
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}

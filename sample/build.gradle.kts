import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.ifsc.sample"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ifsc.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

val usePublishedIfsc = providers.gradleProperty("usePublishedIfsc").orNull == "true"
val publishedVersion = providers.gradleProperty("VERSION").getOrElse("0.1.0")

dependencies {
    // Use the local modules by default so the sample reflects current source.
    // Pass -PusePublishedIfsc=true to verify the published JitPack artifact.
    if (usePublishedIfsc) {
        implementation("com.github.kalactor.ifsc-kotlin:offline:$publishedVersion")
    } else {
        implementation(project(":offline"))
    }

    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

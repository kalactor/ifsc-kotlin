import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    `maven-publish`
}

group = "com.github.kalactor.ifsc-kotlin"
version = providers.gradleProperty("VERSION").getOrElse("0.1.0")

android {
    namespace = "dev.ifsc.offline"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                pom {
                    name.set("IFSC Kotlin Offline")
                    description.set("Offline IFSC branch lookup with a bundled SQLite database for Android.")
                    url.set("https://github.com/kalactor/ifsc-kotlin")
                    licenses {
                        license {
                            name.set("The MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        url.set("https://github.com/kalactor/ifsc-kotlin")
                        connection.set("scm:git:git://github.com/kalactor/ifsc-kotlin.git")
                    }
                }
            }
        }
    }
}

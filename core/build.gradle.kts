import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "com.github.kalactor.ifsc-kotlin"
version = providers.gradleProperty("VERSION").getOrElse("0.1.0")

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("IFSC Kotlin Core")
                description.set("Offline IFSC validation, bank names, and bank metadata for Kotlin/JVM and Android.")
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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure-Kotlin input engine: romanization, fuzzy matching, lexicon lookup and
// composition state. No Android dependencies so it can be unit-tested on the JVM
// and reused by the dictionary builder.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
}

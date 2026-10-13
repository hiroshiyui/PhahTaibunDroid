plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "org.ghostsinthelab.app.phahtaibundroid"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "org.ghostsinthelab.app.phahtaibundroid"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Lists the bundled UI languages in the manifest so users can pick one
        // under Settings → Apps → 拍台文 → Language (Android 13+).
        generateLocaleConfig = true
    }
    bundle {
        // The UI language can be switched in-app (Taigi is offered only there), so every
        // language's resources must be installed, not just the device's.
        language {
            enableSplit = false
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.uiautomator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
base {
    archivesName = "${android.defaultConfig.applicationId}_v${android.defaultConfig.versionName}"
}

val appBuildFile = file("build.gradle.kts")

tasks.register("bumpPatchVersion") {
    description = "Increment versionCode and versionName patch level"
    doLast {
        var content = appBuildFile.readText()

        val versionCodeRegex = Regex("""versionCode\s*=\s*(\d+)""")
        val versionNameRegex = Regex("""versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"""")

        val codeMatch = versionCodeRegex.find(content)
            ?: throw GradleException("versionCode not found")
        val nameMatch = versionNameRegex.find(content)
            ?: throw GradleException("versionName not found")

        val oldCode = codeMatch.groupValues[1].toInt()
        val (major, minor, patch) = nameMatch.destructured
        val newCode = oldCode + 1
        val newVersionName = "$major.$minor.${patch.toInt() + 1}"

        content = content
            .replace(codeMatch.value, "versionCode = $newCode")
            .replace(nameMatch.value, """versionName = "$newVersionName"""")

        appBuildFile.writeText(content)
        println("Version bumped: $oldCode -> $newCode, $major.$minor.$patch -> $newVersionName")
    }
}

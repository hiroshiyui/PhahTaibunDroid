import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Compiles ChhoeTaigi CSV sources (data/sources/) into the app's lexicon asset.
//   ./gradlew :tools:dictbuilder:buildLexicon
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
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

application {
    mainClass.set("org.ghostsinthelab.app.phahtaibundroid.dictbuilder.MainKt")
}

dependencies {
    implementation(project(":core"))
}

val sourcesDir = rootProject.layout.projectDirectory.dir("data/sources")
val lexiconAsset = rootProject.layout.projectDirectory.file("app/src/main/assets/lexicon.tsv")

tasks.register<JavaExec>("buildLexicon") {
    group = "phahtaibun"
    description = "Compiles data/sources/*.csv into app/src/main/assets/lexicon.tsv"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    args(sourcesDir.asFile.absolutePath, lexiconAsset.asFile.absolutePath)
}

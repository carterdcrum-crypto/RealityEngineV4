import java.net.URI
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val buildNumber = ciRun ?: 306
val buildName = "0.$buildNumber.0"
val buildId = "RE4-W$buildNumber"
val updaterKeystore = rootProject.file("realityengine-updater.jks")

val scrcpyVersion = "4.0"
val scrcpyServerSha256 = "84924bd564a1eb6089c872c7521f968058977f91f5ff02514a8c74aff3210f3a"
val scrcpyServerUrl = "https://github.com/Genymobile/scrcpy/releases/download/v$scrcpyVersion/scrcpy-server-v$scrcpyVersion"
val scrcpyAssetName = "scrcpy-server"
val scrcpyAssetDir = layout.buildDirectory.dir("generated/scrcpy/assets")

abstract class DownloadVerifiedScrcpyTask : DefaultTask() {
    @get:OutputDirectory abstract val outputDir: org.gradle.api.file.DirectoryProperty
    @TaskAction fun download() {
        val target = outputDir.get().file(scrcpyAssetName).asFile
        fun hash(file: File): String {
            val d = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val b=ByteArray(8192); while(true){val n=input.read(b);if(n<0)break;if(n>0)d.update(b,0,n)} }
            return d.digest().joinToString("") { "%02x".format(it) }
        }
        if (target.isFile && hash(target).equals(scrcpyServerSha256, true)) return
        target.parentFile.mkdirs()
        URI(scrcpyServerUrl).toURL().openStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        val actual = hash(target)
        if (!actual.equals(scrcpyServerSha256, true)) { target.delete(); throw GradleException("scrcpy-server SHA-256 mismatch") }
    }
}
val downloadVerifiedScrcpy = tasks.register<DownloadVerifiedScrcpyTask>("downloadVerifiedScrcpy") { outputDir.set(scrcpyAssetDir) }

android {
    namespace = "com.realityengine.v4"
    compileSdk = 35

    signingConfigs {
        create("updater") {
            storeFile = updaterKeystore
            storePassword = System.getenv("RE_UPDATER_STORE_PASSWORD") ?: "realityengine-local"
            keyAlias = System.getenv("RE_UPDATER_KEY_ALIAS") ?: "realityengine"
            keyPassword = System.getenv("RE_UPDATER_KEY_PASSWORD") ?: "realityengine-local"
        }
    }

    defaultConfig {
        applicationId = "com.realityengine.v4"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = buildName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BUILD_ID", "\"$buildId\"")
    }

    buildTypes {
        debug { if (updaterKeystore.exists()) signingConfig = signingConfigs.getByName("updater") }
        release { if (updaterKeystore.exists()) signingConfig = signingConfigs.getByName("updater") }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(downloadVerifiedScrcpy, DownloadVerifiedScrcpyTask::outputDir) }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-graphics:1.7.5")
    implementation("androidx.compose.foundation:foundation:1.7.5")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    testImplementation("junit:junit:4.13.2")
}

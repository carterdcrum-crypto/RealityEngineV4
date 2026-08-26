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

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n > 0) digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val downloadVerifiedScrcpy = tasks.register("downloadVerifiedScrcpy") {
    val outputDir = scrcpyAssetDir.get().asFile
    outputs.dir(outputDir)
    doLast {
        val target = File(outputDir, scrcpyAssetName)
        if (!(target.isFile && sha256(target).equals(scrcpyServerSha256, ignoreCase = true))) {
            target.parentFile.mkdirs()
            val temp = File(target.parentFile, "${target.name}.download")
            temp.delete()
            URI(scrcpyServerUrl).toURL().openStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            val actual = sha256(temp)
            if (!actual.equals(scrcpyServerSha256, ignoreCase = true)) {
                temp.delete()
                throw GradleException("scrcpy-server SHA-256 mismatch: $actual")
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        }
    }
}

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
        buildConfigField("String", "SCRCPY_SERVER_ASSET_NAME", "\"$scrcpyAssetName\"")
        buildConfigField("String", "SCRCPY_SERVER_SHA256", "\"$scrcpyServerSha256\"")
        buildConfigField("String", "SCRCPY_SERVER_VERSION", "\"$scrcpyVersion\"")
    }

    sourceSets.getByName("main").assets.srcDir(scrcpyAssetDir)

    buildTypes {
        debug { if (updaterKeystore.exists()) signingConfig = signingConfigs.getByName("updater") }
        release { if (updaterKeystore.exists()) signingConfig = signingConfigs.getByName("updater") }
    }

    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(downloadVerifiedScrcpy)
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

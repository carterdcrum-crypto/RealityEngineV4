package com.realityengine.v4

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Downloads the latest green APK from the private GitHub release and hands it to Android's package installer. */
class AppUpdater(
    private val context: Context,
    private val settingsStore: SettingsStore
) {
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val buildId: String,
        val apkAssetApiUrl: String
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        data class Current(val versionName: String) : CheckResult()
        data class Failed(val reason: String) : CheckResult()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun check(callback: (CheckResult) -> Unit) {
        Thread {
            val result = try {
                val token = settingsStore.githubUpdaterToken.trim()
                if (token.isBlank()) throw IllegalStateException("Private updater token required")

                val release = requestJson(RELEASE_BY_TAG_URL, token)
                val assets = release.getJSONArray("assets")
                var metadataApiUrl: String? = null
                var apkApiUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    when (asset.optString("name")) {
                        METADATA_ASSET -> metadataApiUrl = asset.optString("url")
                        APK_ASSET -> apkApiUrl = asset.optString("url")
                    }
                }

                val metadataUrl = metadataApiUrl?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Private update metadata asset is missing")
                val apkUrl = apkApiUrl?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Private update APK asset is missing")

                val metadataBody = requestAssetBytes(metadataUrl, token).toString(Charsets.UTF_8)
                val json = JSONObject(metadataBody)
                val info = UpdateInfo(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.getString("versionName"),
                    buildId = json.optString("buildId", "latest"),
                    apkAssetApiUrl = apkUrl
                )
                if (info.versionCode > BuildConfig.VERSION_CODE) {
                    CheckResult.Available(info)
                } else {
                    CheckResult.Current(BuildConfig.VERSION_NAME)
                }
            } catch (t: Throwable) {
                CheckResult.Failed(t.message ?: "Private update check failed")
            }
            callback(result)
        }.apply { name = "reality-private-update-check" }.start()
    }

    fun downloadAndInstall(info: UpdateInfo, callback: (String) -> Unit) {
        Thread {
            try {
                val token = settingsStore.githubUpdaterToken.trim()
                if (token.isBlank()) throw IllegalStateException("Private updater token required")
                val apkBytes = requestAssetBytes(info.apkAssetApiUrl, token)
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "RealityEngine-${info.versionCode}.apk")
                apk.outputStream().use { it.write(apkBytes) }

                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(install)
                callback("Installer opened")
            } catch (t: Throwable) {
                callback(t.message ?: "Private update install failed")
            }
        }.apply { name = "reality-private-update-download" }.start()
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        )
    }

    private fun requestJson(url: String, token: String): JSONObject {
        val request = authorizedRequest(url, token)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw githubFailure(response.code)
            response.body?.string() ?: throw IllegalStateException("Empty GitHub update response")
        }
        return JSONObject(body)
    }

    private fun requestAssetBytes(assetApiUrl: String, token: String): ByteArray {
        val request = authorizedRequest(assetApiUrl, token)
            .header("Accept", "application/octet-stream")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw githubFailure(response.code)
            response.body?.bytes() ?: throw IllegalStateException("Empty private update asset")
        }
    }

    private fun authorizedRequest(url: String, token: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "RealityEngineV4/${BuildConfig.VERSION_NAME}")
            .header("Cache-Control", "no-cache")

    private fun githubFailure(code: Int): IllegalStateException = IllegalStateException(
        when (code) {
            401 -> "GitHub updater token was rejected (401)"
            403 -> "GitHub updater token cannot read this private release (403)"
            404 -> "Private updater release was not found or the token cannot access RealityEngineV4 (404)"
            else -> "GitHub private updater returned $code"
        }
    )

    companion object {
        private const val RELEASE_BY_TAG_URL =
            "https://api.github.com/repos/carterdcrum-crypto/RealityEngineV4/releases/tags/updater-latest"
        private const val METADATA_ASSET = "update.json"
        private const val APK_ASSET = "RealityEngineV4-latest.apk"
    }
}

package com.realityengine.v4

import android.annotation.SuppressLint
import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Extracts the pinned scrcpy-server asset to storage readable by the Shizuku shell service. */
object ScrcpyServerAsset {
    @SuppressLint("SetWorldReadable", "SetWorldWritable")
    fun ensureExtracted(context: Context): File? {
        val base = context.getExternalFilesDir("scrcpy") ?: return null
        val target = File(base, "scrcpy-server-${BuildConfig.SCRCPY_SERVER_VERSION}")
        if (target.isFile && verify(target)) return target

        return try {
            base.mkdirs()
            val temp = File(base, "${target.name}.tmp")
            temp.delete()
            context.assets.open(BuildConfig.SCRCPY_SERVER_ASSET_NAME).use { input ->
                temp.outputStream().use { output -> input.copyTo(output, 8192) }
            }
            if (!verify(temp)) {
                temp.delete()
                return null
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target.setReadable(true, false)
            base.setReadable(true, false)
            base.setExecutable(true, false)
            target.takeIf { verify(it) }
        } catch (_: Throwable) {
            null
        }
    }

    fun verify(file: File): Boolean {
        if (!file.isFile) return false
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n > 0) digest.update(buffer, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
                .equals(BuildConfig.SCRCPY_SERVER_SHA256, ignoreCase = true)
        }.getOrDefault(false)
    }
}

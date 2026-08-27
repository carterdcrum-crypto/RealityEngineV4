package com.realityengine.v4

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Local-first caller-memory synchronization over Supabase Auth + PostgREST. */
class SupabaseCallerMemorySync(context: Context) {
    enum class Status { DISABLED, SYNCED, LOCAL_NEWER, CLOUD_NEWER, DELETED, AUTH_REQUIRED, ERROR }

    data class Result(val status: Status, val detail: String = "")

    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val profiles = CallerProfileStore(appContext)
    private val session = SupabaseMemorySession(appContext, settings)
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .writeTimeout(9, TimeUnit.SECONDS)
        .build()

    fun syncAsync(phoneNumber: String, callback: (Result) -> Unit = {}) {
        EXECUTOR.execute {
            val result = runCatching { sync(phoneNumber) }
                .getOrElse { Result(Status.ERROR, it.message.orEmpty().take(160)) }
            callback(result)
        }
    }

    fun pushAsync(phoneNumber: String, callback: (Result) -> Unit = {}) {
        EXECUTOR.execute {
            val result = runCatching { push(phoneNumber) }
                .getOrElse { Result(Status.ERROR, it.message.orEmpty().take(160)) }
            callback(result)
        }
    }

    fun testConnectionAsync(callback: (Result) -> Unit) {
        EXECUTOR.execute {
            val result = runCatching { testConnection() }
                .getOrElse { Result(Status.ERROR, it.message.orEmpty().take(180)) }
            callback(result)
        }
    }

    internal fun testConnection(): Result {
        if (!settings.supabaseConfigured()) return Result(Status.DISABLED, "Project URL and publishable key are required")
        val auth = session.authenticatedSession()
            ?: return Result(Status.AUTH_REQUIRED, "Enable Anonymous Sign-Ins and verify the publishable key")
        val base = SupabaseSyncPolicy.normalizeBaseUrl(settings.supabaseUrl)
        val request = Request.Builder()
            .url("$base/rest/v1/caller_profiles?select=phone_key&limit=1")
            .get()
            .authHeaders(auth)
            .build()
        client.newCall(request).execute().use { response ->
            return when {
                response.isSuccessful -> Result(Status.SYNCED, "auth + caller_profiles table ready")
                response.code == 401 || response.code == 403 -> Result(Status.AUTH_REQUIRED, "Check publishable key, Anonymous Sign-Ins and RLS")
                response.code == 404 -> Result(Status.ERROR, "caller_profiles table missing — run the bundled Supabase SQL")
                else -> Result(Status.ERROR, "Supabase table test HTTP ${response.code}")
            }
        }
    }

    internal fun sync(phoneNumber: String): Result {
        if (!settings.supabaseConfigured()) return Result(Status.DISABLED, "Supabase not configured")
        val clean = phoneNumber.trim()
        if (clean.isBlank() || clean == "UNKNOWN CALLER") return Result(Status.DISABLED, "No caller key")
        val auth = session.authenticatedSession() ?: return Result(Status.AUTH_REQUIRED, "Enable Anonymous Sign-Ins or link an account")

        if (profiles.tombstoneAt(clean) > 0L) {
            return if (deleteRemote(clean, auth)) {
                profiles.clearTombstone(clean)
                Result(Status.DELETED, "Cloud caller memory deleted")
            } else Result(Status.ERROR, "Could not delete cloud caller memory")
        }

        val local = profiles.load(clean)
        val remote = fetchRemote(clean, auth)
        if (remote == null) {
            return if (upsert(local, auth)) Result(Status.SYNCED, "Local profile uploaded")
            else Result(Status.ERROR, "Cloud upload failed")
        }

        return when (SupabaseSyncPolicy.decide(local.updatedAtMs, remote.updatedAtMs)) {
            SupabaseSyncPolicy.Decision.PULL_REMOTE -> {
                val imported = profiles.importCloudJson(clean, remote.profile.toString(), remote.updatedAtMs)
                if (imported != null) Result(Status.CLOUD_NEWER, "Newer cloud profile restored")
                else Result(Status.ERROR, "Cloud profile could not be imported")
            }
            SupabaseSyncPolicy.Decision.PUSH_LOCAL -> {
                if (upsert(local, auth)) Result(Status.LOCAL_NEWER, "Local profile synchronized")
                else Result(Status.ERROR, "Cloud upload failed")
            }
        }
    }

    internal fun push(phoneNumber: String): Result {
        if (!settings.supabaseConfigured()) return Result(Status.DISABLED, "Supabase not configured")
        val clean = phoneNumber.trim()
        if (clean.isBlank() || clean == "UNKNOWN CALLER") return Result(Status.DISABLED, "No caller key")
        val auth = session.authenticatedSession() ?: return Result(Status.AUTH_REQUIRED, "Supabase auth unavailable")
        if (profiles.tombstoneAt(clean) > 0L) {
            return if (deleteRemote(clean, auth)) {
                profiles.clearTombstone(clean)
                Result(Status.DELETED, "Cloud caller memory deleted")
            } else Result(Status.ERROR, "Could not delete cloud caller memory")
        }
        return if (upsert(profiles.load(clean), auth)) Result(Status.SYNCED, "Caller memory synchronized")
        else Result(Status.ERROR, "Cloud upload failed")
    }

    private data class RemoteProfile(val profile: JSONObject, val updatedAtMs: Long)

    private fun fetchRemote(phoneNumber: String, auth: SupabaseMemorySession.Session): RemoteProfile? {
        val base = SupabaseSyncPolicy.normalizeBaseUrl(settings.supabaseUrl)
        val encoded = URLEncoder.encode(phoneNumber, "UTF-8")
        val request = Request.Builder()
            .url("$base/rest/v1/caller_profiles?phone_key=eq.$encoded&select=profile,updated_at_ms&limit=1")
            .get()
            .authHeaders(auth)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Supabase fetch HTTP ${response.code}")
            val array = JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            if (array.length() == 0) return null
            val row = array.getJSONObject(0)
            return RemoteProfile(
                profile = row.optJSONObject("profile") ?: JSONObject(),
                updatedAtMs = row.optLong("updated_at_ms", 0L)
            )
        }
    }

    private fun upsert(profile: CallerProfileStore.CallerProfile, auth: SupabaseMemorySession.Session): Boolean {
        val base = SupabaseSyncPolicy.normalizeBaseUrl(settings.supabaseUrl)
        val body = JSONObject().apply {
            put("owner_id", auth.userId)
            put("phone_key", profile.phoneNumber)
            put("display_name", profile.displayName)
            put("profile", JSONObject(profiles.exportJson(profile.phoneNumber)))
            put("updated_at_ms", profile.updatedAtMs)
        }.toString()
        val request = Request.Builder()
            .url("$base/rest/v1/caller_profiles?on_conflict=owner_id,phone_key")
            .post(body.toRequestBody(JSON))
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .authHeaders(auth)
            .build()
        client.newCall(request).execute().use { return it.isSuccessful }
    }

    private fun deleteRemote(phoneNumber: String, auth: SupabaseMemorySession.Session): Boolean {
        val base = SupabaseSyncPolicy.normalizeBaseUrl(settings.supabaseUrl)
        val encoded = URLEncoder.encode(phoneNumber, "UTF-8")
        val request = Request.Builder()
            .url("$base/rest/v1/caller_profiles?phone_key=eq.$encoded")
            .delete()
            .authHeaders(auth)
            .build()
        client.newCall(request).execute().use { return it.isSuccessful }
    }

    private fun Request.Builder.authHeaders(auth: SupabaseMemorySession.Session): Request.Builder =
        header("apikey", settings.supabaseAnonKey)
            .header("Authorization", "Bearer ${auth.accessToken}")
            .header("Content-Type", "application/json")

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}

package com.realityengine.v4

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Secure Supabase Auth session used by caller-memory sync.
 * Starts with an anonymous authenticated user so RLS can protect rows. A future account-linking
 * layer can attach a permanent login to this same user without changing caller rows.
 */
class SupabaseMemorySession(context: Context, private val settings: SettingsStore = SettingsStore(context)) {
    data class Session(val accessToken: String, val refreshToken: String, val userId: String, val expiresAtMs: Long)

    private val secrets = SecureSecretStore(context.applicationContext)
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .writeTimeout(9, TimeUnit.SECONDS)
        .build()

    @Synchronized
    fun authenticatedSession(): Session? {
        if (!settings.supabaseConfigured()) return null
        val saved = load()
        if (saved != null && saved.expiresAtMs > System.currentTimeMillis() + 60_000L) return saved
        if (saved != null && saved.refreshToken.isNotBlank()) refresh(saved.refreshToken)?.let { return it }
        return signInAnonymously()
    }

    @Synchronized
    fun clear() {
        secrets.put(KEY_ACCESS, "")
        secrets.put(KEY_REFRESH, "")
        secrets.put(KEY_USER, "")
        secrets.put(KEY_EXPIRES, "")
    }

    private fun signInAnonymously(): Session? {
        val base = SupabaseSyncPolicy.normalizeBaseUrl(settings.supabaseUrl)
        if (base.isBlank()) return null
        val request = Request.Builder()
            .url("$base/auth/v1/signup")
            .header("apikey", settings.supabaseAnonKey)
            .header("Content-Type", "application/json")
            .post("{}".toRequestBody(JSON))
            .build()
        return executeSessionRequest(request)
    }

    private fun refresh(refreshToken: String): Session? {
        val base = SupabaseSyncPolicy.normalizeBaseUrl(settings.supabaseUrl)
        val body = JSONObject().put("refresh_token", refreshToken).toString()
        val request = Request.Builder()
            .url("$base/auth/v1/token?grant_type=refresh_token")
            .header("apikey", settings.supabaseAnonKey)
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        return executeSessionRequest(request)
    }

    private fun executeSessionRequest(request: Request): Session? = runCatching {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val root = JSONObject(response.body?.string().orEmpty())
            val access = root.optString("access_token")
            val refresh = root.optString("refresh_token")
            val user = root.optJSONObject("user")?.optString("id").orEmpty()
            val expiresIn = root.optLong("expires_in", 3600L).coerceAtLeast(60L)
            if (access.isBlank() || refresh.isBlank() || user.isBlank()) return@use null
            val session = Session(access, refresh, user, System.currentTimeMillis() + expiresIn * 1000L)
            save(session)
            session
        }
    }.getOrNull()

    private fun load(): Session? {
        val access = secrets.get(KEY_ACCESS)
        val refresh = secrets.get(KEY_REFRESH)
        val user = secrets.get(KEY_USER)
        val expires = secrets.get(KEY_EXPIRES).toLongOrNull() ?: 0L
        if (access.isBlank() || refresh.isBlank() || user.isBlank()) return null
        return Session(access, refresh, user, expires)
    }

    private fun save(session: Session) {
        secrets.put(KEY_ACCESS, session.accessToken)
        secrets.put(KEY_REFRESH, session.refreshToken)
        secrets.put(KEY_USER, session.userId)
        secrets.put(KEY_EXPIRES, session.expiresAtMs.toString())
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val KEY_ACCESS = "supabase_memory_access"
        private const val KEY_REFRESH = "supabase_memory_refresh"
        private const val KEY_USER = "supabase_memory_user"
        private const val KEY_EXPIRES = "supabase_memory_expires"
    }
}

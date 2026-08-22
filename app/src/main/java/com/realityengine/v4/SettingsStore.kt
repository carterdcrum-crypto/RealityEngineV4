package com.realityengine.v4

import android.content.Context

/** Persistent configuration backing the reference-style settings UI. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reality_engine_settings", Context.MODE_PRIVATE)

    var groqApiKey: String
        get() = prefs.getString("groq_api_key", "").orEmpty()
        set(value) = prefs.edit().putString("groq_api_key", value.trim()).apply()

    var groqModel: String
        get() = prefs.getString("groq_model", "llama-3.1-8b-instant").orEmpty()
        set(value) = prefs.edit().putString("groq_model", value).apply()

    var deepgramApiKey: String
        get() = prefs.getString("deepgram_api_key", "").orEmpty()
        set(value) = prefs.edit().putString("deepgram_api_key", value.trim()).apply()

    var deepgramModel: String
        get() = prefs.getString("deepgram_model", "nova-2-phonecall").orEmpty()
        set(value) = prefs.edit().putString("deepgram_model", value).apply()

    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "").orEmpty()
        set(value) = prefs.edit().putString("supabase_url", value.trim()).apply()

    var supabaseAnonKey: String
        get() = prefs.getString("supabase_anon_key", "").orEmpty()
        set(value) = prefs.edit().putString("supabase_anon_key", value.trim()).apply()

    var responseCoachEnabled: Boolean
        get() = prefs.getBoolean("response_coach_enabled", true)
        set(value) = prefs.edit().putBoolean("response_coach_enabled", value).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics_enabled", true)
        set(value) = prefs.edit().putBoolean("haptics_enabled", value).apply()

    var analysisFrequencySeconds: Int
        get() = prefs.getInt("analysis_frequency_seconds", 3).coerceIn(1, 30)
        set(value) = prefs.edit().putInt("analysis_frequency_seconds", value.coerceIn(1, 30)).apply()

    fun groqConfigured() = groqApiKey.isNotBlank()
    fun deepgramConfigured() = deepgramApiKey.isNotBlank()
    fun supabaseConfigured() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}

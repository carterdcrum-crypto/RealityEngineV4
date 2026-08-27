package com.realityengine.v4

import android.content.Context

/** Persistent configuration backing the reference-style settings UI. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reality_engine_settings", Context.MODE_PRIVATE)
    private val secrets = SecureSecretStore(context.applicationContext)

    var groqApiKey: String
        get() = prefs.getString("groq_api_key", "").orEmpty()
        set(value) = prefs.edit().putString("groq_api_key", value.trim()).apply()

    var groqModel: String
        get() {
            if (!prefs.getBoolean(KEY_GROQ_BALANCED_MIGRATED, false)) {
                prefs.edit().putString("groq_model", DEFAULT_GROQ_MODEL).putBoolean(KEY_GROQ_BALANCED_MIGRATED, true).apply()
                return DEFAULT_GROQ_MODEL
            }
            val saved = prefs.getString("groq_model", DEFAULT_GROQ_MODEL).orEmpty().trim()
            val normalized = saved.takeIf { it in GROQ_MODELS } ?: DEFAULT_GROQ_MODEL
            if (saved != normalized) prefs.edit().putString("groq_model", normalized).apply()
            return normalized
        }
        set(value) {
            val clean = value.trim()
            prefs.edit().putString("groq_model", clean.takeIf { it in GROQ_MODELS } ?: DEFAULT_GROQ_MODEL).putBoolean(KEY_GROQ_BALANCED_MIGRATED, true).apply()
        }

    fun resetGroqModel() {
        prefs.edit().putString("groq_model", DEFAULT_GROQ_MODEL).putBoolean(KEY_GROQ_BALANCED_MIGRATED, true).apply()
    }

    var deepgramApiKey: String
        get() = prefs.getString("deepgram_api_key", "").orEmpty()
        set(value) = prefs.edit().putString("deepgram_api_key", value.trim()).apply()

    var deepgramModel: String
        get() {
            if (!prefs.getBoolean(KEY_NOVA3_MIGRATED, false)) {
                prefs.edit().putString("deepgram_model", DEFAULT_DEEPGRAM_MODEL).putBoolean(KEY_NOVA3_MIGRATED, true).apply()
                return DEFAULT_DEEPGRAM_MODEL
            }
            val saved = prefs.getString("deepgram_model", DEFAULT_DEEPGRAM_MODEL).orEmpty().trim()
            val normalized = saved.takeIf { it in DEEPGRAM_MODELS } ?: DEFAULT_DEEPGRAM_MODEL
            if (saved != normalized) prefs.edit().putString("deepgram_model", normalized).apply()
            return normalized
        }
        set(value) {
            val clean = value.trim()
            prefs.edit().putString("deepgram_model", clean.takeIf { it in DEEPGRAM_MODELS } ?: DEFAULT_DEEPGRAM_MODEL).putBoolean(KEY_NOVA3_MIGRATED, true).apply()
        }

    var supabaseUrl: String
        get() = prefs.getString("supabase_url", "").orEmpty()
        set(value) = prefs.edit().putString("supabase_url", value.trim()).apply()

    var supabaseAnonKey: String
        get() = prefs.getString("supabase_anon_key", "").orEmpty()
        set(value) = prefs.edit().putString("supabase_anon_key", value.trim()).apply()

    var twilioMediaWebSocketUrl: String
        get() = prefs.getString("twilio_media_websocket_url", "").orEmpty()
        set(value) = prefs.edit().putString("twilio_media_websocket_url", value.trim()).apply()

    var twilioAccessTokenEndpoint: String
        get() = prefs.getString("twilio_access_token_endpoint", "").orEmpty()
        set(value) = prefs.edit().putString("twilio_access_token_endpoint", value.trim()).apply()

    var githubUpdaterToken: String
        get() = secrets.get("github_updater_token")
        set(value) = secrets.put("github_updater_token", value)

    var responseCoachEnabled: Boolean
        get() = prefs.getBoolean("response_coach_enabled", true)
        set(value) = prefs.edit().putBoolean("response_coach_enabled", value).apply()

    /** Global delivery style. Individual callers can override this with their own persona. */
    var coachPersonaId: String
        get() {
            val saved = prefs.getString("coach_persona_id", DEFAULT_COACH_PERSONA).orEmpty()
            val normalized = CoachPersonaCatalog.normalize(saved, DEFAULT_COACH_PERSONA)
            if (saved != normalized) prefs.edit().putString("coach_persona_id", normalized).apply()
            return normalized
        }
        set(value) = prefs.edit().putString("coach_persona_id", CoachPersonaCatalog.normalize(value, DEFAULT_COACH_PERSONA)).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics_enabled", true)
        set(value) = prefs.edit().putBoolean("haptics_enabled", value).apply()

    /** Off by default. When enabled, active call audio is visibly recorded and reviewed after every call. */
    var autoRecordCalls: Boolean
        get() = prefs.getBoolean("auto_record_calls", false)
        set(value) = prefs.edit().putBoolean("auto_record_calls", value).apply()

    var analysisFrequencyTurns: Int
        get() {
            if (prefs.contains("analysis_frequency_turns")) return prefs.getInt("analysis_frequency_turns", 1).coerceIn(1, 10)
            prefs.edit().remove("analysis_frequency_seconds").putInt("analysis_frequency_turns", 1).apply()
            return 1
        }
        set(value) = prefs.edit().putInt("analysis_frequency_turns", value.coerceIn(1, 10)).apply()

    fun groqConfigured() = groqApiKey.isNotBlank()
    fun deepgramConfigured() = deepgramApiKey.isNotBlank()
    fun supabaseConfigured() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
    fun twilioMediaConfigured() = twilioMediaWebSocketUrl.startsWith("wss://") && twilioAccessTokenEndpoint.startsWith("https://")
    fun privateUpdaterConfigured() = githubUpdaterToken.isNotBlank()

    companion object {
        const val DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b"
        val GROQ_MODELS = listOf(DEFAULT_GROQ_MODEL, "llama-3.3-70b-versatile", "llama-3.1-8b-instant")

        const val DEFAULT_DEEPGRAM_MODEL = "nova-3"
        val DEEPGRAM_MODELS = listOf(DEFAULT_DEEPGRAM_MODEL, "nova-2-phonecall")

        const val DEFAULT_COACH_PERSONA = "ADAPTIVE"

        private const val KEY_GROQ_BALANCED_MIGRATED = "groq_balanced_20b_migrated"
        private const val KEY_NOVA3_MIGRATED = "deepgram_nova3_migrated"
    }
}

package com.realityengine.v4

import android.content.Context

/** Persistent configuration backing the reference-style settings UI. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("reality_engine_settings", Context.MODE_PRIVATE)
    private val secrets = SecureSecretStore(context.applicationContext)

    var coachProvider: String
        get() {
            val saved = prefs.getString(KEY_COACH_PROVIDER, DEFAULT_COACH_PROVIDER).orEmpty().trim().uppercase()
            val normalized = saved.takeIf { it in COACH_PROVIDERS } ?: DEFAULT_COACH_PROVIDER
            if (saved != normalized) prefs.edit().putString(KEY_COACH_PROVIDER, normalized).apply()
            return normalized
        }
        set(value) {
            val clean = value.trim().uppercase()
            prefs.edit().putString(KEY_COACH_PROVIDER, clean.takeIf { it in COACH_PROVIDERS } ?: DEFAULT_COACH_PROVIDER).apply()
        }

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

    /** Provider API keys below are stored in the app's encrypted secret store. */
    var geminiApiKey: String
        get() = secrets.get(KEY_GEMINI_API_KEY)
        set(value) = secrets.put(KEY_GEMINI_API_KEY, value.trim())

    var geminiModel: String
        get() {
            val saved = prefs.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL).orEmpty().trim()
            val normalized = saved.takeIf { it in GEMINI_MODELS } ?: DEFAULT_GEMINI_MODEL
            if (saved != normalized) prefs.edit().putString(KEY_GEMINI_MODEL, normalized).apply()
            return normalized
        }
        set(value) {
            val clean = value.trim()
            prefs.edit().putString(KEY_GEMINI_MODEL, clean.takeIf { it in GEMINI_MODELS } ?: DEFAULT_GEMINI_MODEL).apply()
        }

    var cerebrasApiKey: String
        get() = secrets.get(KEY_CEREBRAS_API_KEY)
        set(value) = secrets.put(KEY_CEREBRAS_API_KEY, value.trim())

    var cerebrasModel: String
        get() = prefs.getString(KEY_CEREBRAS_MODEL, DEFAULT_CEREBRAS_MODEL).orEmpty().trim()
            .takeIf { it in CEREBRAS_MODELS } ?: DEFAULT_CEREBRAS_MODEL
        set(value) = prefs.edit().putString(
            KEY_CEREBRAS_MODEL,
            value.trim().takeIf { it in CEREBRAS_MODELS } ?: DEFAULT_CEREBRAS_MODEL
        ).apply()

    var mistralApiKey: String
        get() = secrets.get(KEY_MISTRAL_API_KEY)
        set(value) = secrets.put(KEY_MISTRAL_API_KEY, value.trim())

    var mistralModel: String
        get() = prefs.getString(KEY_MISTRAL_MODEL, DEFAULT_MISTRAL_MODEL).orEmpty().trim()
            .takeIf { it in MISTRAL_MODELS } ?: DEFAULT_MISTRAL_MODEL
        set(value) = prefs.edit().putString(
            KEY_MISTRAL_MODEL,
            value.trim().takeIf { it in MISTRAL_MODELS } ?: DEFAULT_MISTRAL_MODEL
        ).apply()

    var openRouterApiKey: String
        get() = secrets.get(KEY_OPENROUTER_API_KEY)
        set(value) = secrets.put(KEY_OPENROUTER_API_KEY, value.trim())

    var openRouterModel: String
        get() = prefs.getString(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL).orEmpty().trim()
            .takeIf { it in OPENROUTER_MODELS } ?: DEFAULT_OPENROUTER_MODEL
        set(value) = prefs.edit().putString(
            KEY_OPENROUTER_MODEL,
            value.trim().takeIf { it in OPENROUTER_MODELS } ?: DEFAULT_OPENROUTER_MODEL
        ).apply()

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
        get() = prefs.getString(KEY_SUPABASE_URL, "").orEmpty()
        set(value) {
            val clean = value.trim()
            val changed = clean != supabaseUrl
            val editor = prefs.edit().putString(KEY_SUPABASE_URL, clean)
            if (changed) editor.remove(KEY_SUPABASE_VERIFIED_AT_MS)
            editor.apply()
        }

    var supabaseAnonKey: String
        get() = prefs.getString(KEY_SUPABASE_ANON_KEY, "").orEmpty()
        set(value) {
            val clean = value.trim()
            val changed = clean != supabaseAnonKey
            val editor = prefs.edit().putString(KEY_SUPABASE_ANON_KEY, clean)
            if (changed) editor.remove(KEY_SUPABASE_VERIFIED_AT_MS)
            editor.apply()
        }

    fun saveSupabaseCredentials(url: String, anonKey: String): Boolean {
        val cleanUrl = url.trim().trimEnd('/')
        val cleanKey = anonKey.trim()
        val changed = cleanUrl != supabaseUrl || cleanKey != supabaseAnonKey
        val editor = prefs.edit()
            .putString(KEY_SUPABASE_URL, cleanUrl)
            .putString(KEY_SUPABASE_ANON_KEY, cleanKey)
        if (changed) editor.remove(KEY_SUPABASE_VERIFIED_AT_MS)
        return editor.commit()
    }

    fun clearSupabaseCredentials(): Boolean = prefs.edit()
        .remove(KEY_SUPABASE_URL)
        .remove(KEY_SUPABASE_ANON_KEY)
        .remove(KEY_SUPABASE_VERIFIED_AT_MS)
        .commit()

    val supabaseVerifiedAtMs: Long
        get() = prefs.getLong(KEY_SUPABASE_VERIFIED_AT_MS, 0L)

    fun markSupabaseVerified() {
        prefs.edit().putLong(KEY_SUPABASE_VERIFIED_AT_MS, System.currentTimeMillis()).apply()
    }

    fun clearSupabaseVerification() {
        prefs.edit().remove(KEY_SUPABASE_VERIFIED_AT_MS).apply()
    }

    fun supabaseVerified(): Boolean = supabaseConfigured() && supabaseVerifiedAtMs > 0L

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
    fun geminiConfigured() = geminiApiKey.isNotBlank()
    fun cerebrasConfigured() = cerebrasApiKey.isNotBlank()
    fun mistralConfigured() = mistralApiKey.isNotBlank()
    fun openRouterConfigured() = openRouterApiKey.isNotBlank()

    fun providerConfigured(provider: String): Boolean = when (provider) {
        COACH_PROVIDER_GROQ -> groqConfigured()
        COACH_PROVIDER_GEMINI -> geminiConfigured()
        COACH_PROVIDER_CEREBRAS -> cerebrasConfigured()
        COACH_PROVIDER_MISTRAL -> mistralConfigured()
        COACH_PROVIDER_OPENROUTER -> openRouterConfigured()
        else -> false
    }

    fun coachConfigured(): Boolean = when (coachProvider) {
        COACH_PROVIDER_AUTO -> COACH_FALLBACK_ORDER.any(::providerConfigured)
        else -> providerConfigured(coachProvider)
    }

    fun deepgramConfigured() = deepgramApiKey.isNotBlank()
    fun supabaseConfigured() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
    fun twilioMediaConfigured() = twilioMediaWebSocketUrl.startsWith("wss://") && twilioAccessTokenEndpoint.startsWith("https://")
    fun privateUpdaterConfigured() = githubUpdaterToken.isNotBlank()

    fun coachModelLabel(): String = when (coachProvider) {
        COACH_PROVIDER_GEMINI -> geminiModel
        COACH_PROVIDER_GROQ -> groqModel
        COACH_PROVIDER_CEREBRAS -> cerebrasModel
        COACH_PROVIDER_MISTRAL -> mistralModel
        COACH_PROVIDER_OPENROUTER -> openRouterModel
        else -> COACH_FALLBACK_ORDER.firstOrNull(::providerConfigured)?.let { provider ->
            when (provider) {
                COACH_PROVIDER_GROQ -> groqModel
                COACH_PROVIDER_GEMINI -> geminiModel
                COACH_PROVIDER_CEREBRAS -> cerebrasModel
                COACH_PROVIDER_MISTRAL -> mistralModel
                else -> openRouterModel
            }
        } ?: "no-provider"
    }

    companion object {
        const val COACH_PROVIDER_AUTO = "AUTO"
        const val COACH_PROVIDER_GROQ = "GROQ"
        const val COACH_PROVIDER_GEMINI = "GEMINI"
        const val COACH_PROVIDER_CEREBRAS = "CEREBRAS"
        const val COACH_PROVIDER_MISTRAL = "MISTRAL"
        const val COACH_PROVIDER_OPENROUTER = "OPENROUTER"
        const val DEFAULT_COACH_PROVIDER = COACH_PROVIDER_AUTO
        val COACH_PROVIDERS = listOf(
            COACH_PROVIDER_AUTO,
            COACH_PROVIDER_GROQ,
            COACH_PROVIDER_GEMINI,
            COACH_PROVIDER_CEREBRAS,
            COACH_PROVIDER_MISTRAL,
            COACH_PROVIDER_OPENROUTER,
        )
        val COACH_FALLBACK_ORDER = listOf(
            COACH_PROVIDER_GROQ,
            COACH_PROVIDER_GEMINI,
            COACH_PROVIDER_CEREBRAS,
            COACH_PROVIDER_MISTRAL,
            COACH_PROVIDER_OPENROUTER,
        )

        const val DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b"
        val GROQ_MODELS = listOf(DEFAULT_GROQ_MODEL)

        const val DEFAULT_GEMINI_MODEL = "gemini-3.7-flash"
        val GEMINI_MODELS = listOf(DEFAULT_GEMINI_MODEL, "gemini-2.5-flash", "gemini-2.5-flash-lite")

        const val DEFAULT_CEREBRAS_MODEL = "gpt-oss-120b"
        val CEREBRAS_MODELS = listOf(DEFAULT_CEREBRAS_MODEL)

        const val DEFAULT_MISTRAL_MODEL = "mistral-small-2603"
        val MISTRAL_MODELS = listOf(DEFAULT_MISTRAL_MODEL)

        const val DEFAULT_OPENROUTER_MODEL = "openrouter/free"
        val OPENROUTER_MODELS = listOf(DEFAULT_OPENROUTER_MODEL)

        const val DEFAULT_DEEPGRAM_MODEL = "nova-3"
        val DEEPGRAM_MODELS = listOf(DEFAULT_DEEPGRAM_MODEL, "nova-2-phonecall")

        const val DEFAULT_COACH_PERSONA = "ADAPTIVE"

        private const val KEY_COACH_PROVIDER = "coach_provider"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_CEREBRAS_API_KEY = "cerebras_api_key"
        private const val KEY_CEREBRAS_MODEL = "cerebras_model"
        private const val KEY_MISTRAL_API_KEY = "mistral_api_key"
        private const val KEY_MISTRAL_MODEL = "mistral_model"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_OPENROUTER_MODEL = "openrouter_model"
        private const val KEY_SUPABASE_URL = "supabase_url"
        private const val KEY_SUPABASE_ANON_KEY = "supabase_anon_key"
        private const val KEY_SUPABASE_VERIFIED_AT_MS = "supabase_verified_at_ms"
        private const val KEY_GROQ_BALANCED_MIGRATED = "groq_balanced_20b_migrated"
        private const val KEY_NOVA3_MIGRATED = "deepgram_nova3_migrated"
    }
}

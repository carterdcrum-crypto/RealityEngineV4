package com.realityengine.v4

import android.content.Context

/** User preference layered on top of adaptive provider performance routing. */
class CoachRoutingPreferenceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var preferredProvider: String
        get() {
            val saved = prefs.getString(KEY_PREFERRED_PROVIDER, BEST).orEmpty().trim().uppercase()
            return saved.takeIf { it in OPTIONS } ?: BEST
        }
        set(value) {
            val clean = value.trim().uppercase().takeIf { it in OPTIONS } ?: BEST
            prefs.edit().putString(KEY_PREFERRED_PROVIDER, clean).apply()
        }

    companion object {
        const val BEST = "BEST"
        private const val PREFS = "coach_routing_preference_v1"
        private const val KEY_PREFERRED_PROVIDER = "preferred_provider"

        val OPTIONS = listOf(
            BEST,
            SettingsStore.COACH_PROVIDER_GROQ,
            SettingsStore.COACH_PROVIDER_GEMINI,
            SettingsStore.COACH_PROVIDER_CEREBRAS,
            SettingsStore.COACH_PROVIDER_MISTRAL,
            SettingsStore.COACH_PROVIDER_OPENROUTER,
        )

        /**
         * BEST preserves adaptive order. A preferred provider moves to the front only while healthy;
         * cooled-down or unavailable providers stay where adaptive routing put them.
         */
        internal fun applyPreference(
            adaptiveOrder: List<String>,
            preferredProvider: String,
            preferredCooldownUntilMs: Long = 0L,
            nowMs: Long = System.currentTimeMillis(),
        ): List<String> {
            if (adaptiveOrder.size <= 1) return adaptiveOrder
            val preferred = preferredProvider.trim().uppercase()
            if (preferred == BEST || preferred !in adaptiveOrder || preferredCooldownUntilMs > nowMs) return adaptiveOrder
            return listOf(preferred) + adaptiveOrder.filterNot { it == preferred }
        }
    }
}

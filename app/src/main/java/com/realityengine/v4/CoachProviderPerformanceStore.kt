package com.realityengine.v4

import android.content.Context
import kotlin.math.sqrt

/**
 * Learns which configured response-coach provider is actually fastest and most reliable on-device.
 * No transcript text is stored here: only aggregate success/failure/latency telemetry.
 */
class CoachProviderPerformanceStore(context: Context) {
    data class Stats(
        val provider: String,
        val attempts: Int = 0,
        val successes: Int = 0,
        val consecutiveFailures: Int = 0,
        val emaLatencyMs: Long = 0L,
        val lastSuccessAtMs: Long = 0L,
        val lastFailureAtMs: Long = 0L,
        val cooldownUntilMs: Long = 0L,
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Returns the provider order Auto should try for one coach request.
     * Every eighth Auto request intentionally explores the least-tested eligible provider first.
     */
    @Synchronized
    fun rankedProviders(configuredProviders: List<String>, nowMs: Long = System.currentTimeMillis()): List<String> {
        if (configuredProviders.size <= 1) return configuredProviders
        val requestNumber = prefs.getLong(KEY_AUTO_REQUEST_COUNT, 0L) + 1L
        prefs.edit().putLong(KEY_AUTO_REQUEST_COUNT, requestNumber).apply()
        val samples = configuredProviders.map(::load)
        return rank(samples, configuredProviders, requestNumber, nowMs)
    }

    @Synchronized
    fun recordSuccess(provider: String, latencyMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val old = load(provider)
        val latency = latencyMs.coerceIn(1L, 60_000L)
        val ema = if (old.emaLatencyMs <= 0L) latency else ((old.emaLatencyMs * 7L) + (latency * 3L)) / 10L
        save(old.copy(
            attempts = old.attempts + 1,
            successes = old.successes + 1,
            consecutiveFailures = 0,
            emaLatencyMs = ema,
            lastSuccessAtMs = nowMs,
            cooldownUntilMs = 0L,
        ))
    }

    @Synchronized
    fun recordFailure(provider: String, error: Throwable, latencyMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val old = load(provider)
        val failures = old.consecutiveFailures + 1
        val cooldownMs = failureCooldownMs(error.message.orEmpty(), failures)
        val latency = latencyMs.coerceIn(1L, 60_000L)
        val ema = if (old.emaLatencyMs <= 0L) latency else ((old.emaLatencyMs * 8L) + (latency * 2L)) / 10L
        save(old.copy(
            attempts = old.attempts + 1,
            consecutiveFailures = failures,
            emaLatencyMs = ema,
            lastFailureAtMs = nowMs,
            cooldownUntilMs = nowMs + cooldownMs,
        ))
    }

    @Synchronized
    fun stats(provider: String): Stats = load(provider)

    @Synchronized
    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun load(provider: String): Stats {
        val key = provider.uppercase()
        return Stats(
            provider = key,
            attempts = prefs.getInt("${key}_attempts", 0),
            successes = prefs.getInt("${key}_successes", 0),
            consecutiveFailures = prefs.getInt("${key}_failure_streak", 0),
            emaLatencyMs = prefs.getLong("${key}_latency", 0L),
            lastSuccessAtMs = prefs.getLong("${key}_last_success", 0L),
            lastFailureAtMs = prefs.getLong("${key}_last_failure", 0L),
            cooldownUntilMs = prefs.getLong("${key}_cooldown_until", 0L),
        )
    }

    private fun save(stats: Stats) {
        val key = stats.provider.uppercase()
        prefs.edit()
            .putInt("${key}_attempts", stats.attempts)
            .putInt("${key}_successes", stats.successes)
            .putInt("${key}_failure_streak", stats.consecutiveFailures)
            .putLong("${key}_latency", stats.emaLatencyMs)
            .putLong("${key}_last_success", stats.lastSuccessAtMs)
            .putLong("${key}_last_failure", stats.lastFailureAtMs)
            .putLong("${key}_cooldown_until", stats.cooldownUntilMs)
            .apply()
    }

    companion object {
        private const val PREFS = "coach_provider_performance_v1"
        private const val KEY_AUTO_REQUEST_COUNT = "auto_request_count"
        private const val EXPLORE_EVERY = 8L

        /** Pure ranking function kept visible to unit tests. */
        internal fun rank(
            samples: List<Stats>,
            fallbackOrder: List<String>,
            requestNumber: Long,
            nowMs: Long,
        ): List<String> {
            if (samples.isEmpty()) return emptyList()
            val orderIndex = fallbackOrder.withIndex().associate { it.value to it.index }
            val available = samples.filter { it.cooldownUntilMs <= nowMs }
            val cooled = samples.filter { it.cooldownUntilMs > nowMs }
                .sortedWith(compareBy<Stats> { it.cooldownUntilMs }.thenBy { orderIndex[it.provider] ?: Int.MAX_VALUE })
            val pool = if (available.isNotEmpty()) available else samples
            val scored = pool.sortedWith(
                compareByDescending<Stats> { score(it) }
                    .thenBy { orderIndex[it.provider] ?: Int.MAX_VALUE }
            ).toMutableList()

            if (requestNumber > 0L && requestNumber % EXPLORE_EVERY == 0L && scored.size > 1) {
                val leastTested = scored.minWithOrNull(
                    compareBy<Stats> { it.attempts }
                        .thenBy { orderIndex[it.provider] ?: Int.MAX_VALUE }
                )
                if (leastTested != null) {
                    scored.remove(leastTested)
                    scored.add(0, leastTested)
                }
            }

            val main = scored.map { it.provider }
            if (available.isEmpty()) return main
            return main + cooled.map { it.provider }
        }

        internal fun score(stats: Stats): Double {
            val attempts = stats.attempts.coerceAtLeast(0)
            // Bayesian prior keeps a provider from being permanently buried by one unlucky request.
            val reliability = (stats.successes + 3.0) / (attempts + 3.6)
            val latency = if (stats.emaLatencyMs > 0L) stats.emaLatencyMs.toDouble() else priorLatencyMs(stats.provider)
            val latencyScore = 1.0 / (1.0 + (latency / 1_400.0))
            val explorationBonus = 0.08 / sqrt(attempts + 1.0)
            val failurePenalty = (stats.consecutiveFailures * 0.12).coerceAtMost(0.36)
            return reliability * 0.67 + latencyScore * 0.25 + explorationBonus - failurePenalty
        }

        internal fun failureCooldownMs(message: String, streak: Int): Long {
            val upper = message.uppercase()
            return when {
                "429" in upper || "RATE LIMIT" in upper -> 5 * 60_000L
                "401" in upper || "403" in upper || "AUTH" in upper || "ACCESS DENIED" in upper -> 10 * 60_000L
                "404" in upper || "MODEL NOT FOUND" in upper || "ENDPOINT NOT FOUND" in upper -> 5 * 60_000L
                "TIMEOUT" in upper -> 45_000L
                streak >= 3 -> 3 * 60_000L
                streak == 2 -> 60_000L
                else -> 15_000L
            }
        }

        private fun priorLatencyMs(provider: String): Double = when (provider.uppercase()) {
            SettingsStore.COACH_PROVIDER_CEREBRAS -> 650.0
            SettingsStore.COACH_PROVIDER_GROQ -> 750.0
            SettingsStore.COACH_PROVIDER_MISTRAL -> 1_150.0
            SettingsStore.COACH_PROVIDER_GEMINI -> 1_300.0
            SettingsStore.COACH_PROVIDER_OPENROUTER -> 1_800.0
            else -> 1_500.0
        }
    }
}

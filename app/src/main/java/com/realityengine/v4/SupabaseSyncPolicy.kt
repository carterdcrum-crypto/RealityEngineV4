package com.realityengine.v4

/** Pure conflict rules for local-first caller memory synchronization. */
object SupabaseSyncPolicy {
    enum class Decision { PULL_REMOTE, PUSH_LOCAL }

    fun decide(localUpdatedAtMs: Long, remoteUpdatedAtMs: Long): Decision =
        if (remoteUpdatedAtMs > localUpdatedAtMs) Decision.PULL_REMOTE else Decision.PUSH_LOCAL

    fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')
}

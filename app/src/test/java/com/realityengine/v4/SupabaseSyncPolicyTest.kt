package com.realityengine.v4

import org.junit.Assert.assertEquals
import org.junit.Test

class SupabaseSyncPolicyTest {
    @Test
    fun newerCloudProfileWins() {
        assertEquals(
            SupabaseSyncPolicy.Decision.PULL_REMOTE,
            SupabaseSyncPolicy.decide(localUpdatedAtMs = 100L, remoteUpdatedAtMs = 101L),
        )
    }

    @Test
    fun equalOrNewerLocalProfilePushes() {
        assertEquals(
            SupabaseSyncPolicy.Decision.PUSH_LOCAL,
            SupabaseSyncPolicy.decide(localUpdatedAtMs = 101L, remoteUpdatedAtMs = 101L),
        )
        assertEquals(
            SupabaseSyncPolicy.Decision.PUSH_LOCAL,
            SupabaseSyncPolicy.decide(localUpdatedAtMs = 102L, remoteUpdatedAtMs = 101L),
        )
    }

    @Test
    fun baseUrlRemovesWhitespaceAndTrailingSlash() {
        assertEquals(
            "https://example.supabase.co",
            SupabaseSyncPolicy.normalizeBaseUrl("  https://example.supabase.co/  "),
        )
    }
}

package com.realityengine.v4

import android.content.Context
import kotlin.math.abs

/**
 * Session layer between signal producers, the live call UI, haptics, and persistent caller profiles.
 */
class LiveEvidenceEngine(context: Context) {
    data class Snapshot(
        val phoneNumber: String,
        val acoustic: Int,
        val linguistic: Int,
        val factual: Int,
        val combined: Int,
        val elevatedStreams: Int,
        val persisted: Boolean,
        val timestampMs: Long = System.currentTimeMillis()
    )

    private val fusion = EvidenceFusionEngine()
    private val profiles = CallerProfileStore(context.applicationContext)
    private val haptics = SignalHaptics(context.applicationContext)
    private var lastPersistAt = 0L
    private var lastPersistedCombined = -1f
    private var lastPhone = ""

    @Synchronized
    fun update(
        phoneNumber: String,
        acoustic: Int,
        linguistic: Int,
        factual: Int,
        transcriptContext: String = ""
    ): Snapshot {
        val cleanPhone = phoneNumber.trim()
        val result = fusion.fuse(
            EvidenceFusionEngine.Streams(
                acoustic = acoustic.coerceIn(0, 100) / 100f,
                linguistic = linguistic.coerceIn(0, 100) / 100f,
                factual = factual.coerceIn(0, 100) / 100f
            )
        )

        if (cleanPhone != lastPhone) {
            lastPhone = cleanPhone
            lastPersistAt = 0L
            lastPersistedCombined = -1f
        }

        val now = System.currentTimeMillis()
        val meaningful = result.combined >= 0.65f || result.elevatedStreams >= 2 || result.factual >= 0.75f
        val materiallyChanged = lastPersistedCombined < 0f || abs(result.combined - lastPersistedCombined) >= 0.12f
        val cooldownElapsed = now - lastPersistAt >= 15_000L
        val canPersist = cleanPhone.isNotBlank() && cleanPhone != "UNKNOWN CALLER"
        val shouldPersist = canPersist && meaningful && materiallyChanged && cooldownElapsed

        if (shouldPersist) {
            profiles.recordEvidence(cleanPhone, fusion.toProfileEvent(result, transcriptContext))
            lastPersistAt = now
            lastPersistedCombined = result.combined
        }

        val snapshot = Snapshot(
            phoneNumber = cleanPhone,
            acoustic = (result.acoustic * 100f).toInt(),
            linguistic = (result.linguistic * 100f).toInt(),
            factual = (result.factual * 100f).toInt(),
            combined = (result.combined * 100f).toInt(),
            elevatedStreams = result.elevatedStreams,
            persisted = shouldPersist,
            timestampMs = now
        )
        LiveSignalState.publish(snapshot)
        haptics.update(snapshot.acoustic, snapshot.linguistic, snapshot.factual)
        return snapshot
    }
}

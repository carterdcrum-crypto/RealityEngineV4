package com.realityengine.v4

import android.content.Context
import kotlin.math.abs

/** Session layer between signal producers, the live call UI, haptics, and persistent caller profiles. */
class LiveEvidenceEngine(context: Context) {
    data class Snapshot(
        val phoneNumber: String,
        val acoustic: Int,
        val linguistic: Int,
        val factual: Int,
        val combined: Int,
        val elevatedStreams: Int,
        val persisted: Boolean,
        val cognitiveStress: Int = 0,
        val logOdds: Double = 0.0,
        val timestampMs: Long = System.currentTimeMillis(),
        val context: String = "",
    )

    private val fusion = EvidenceFusionEngine()
    private val cognitive = CognitiveStressEngine()
    private val profiles = CallerProfileStore(context.applicationContext)
    private var lastPersistAt = 0L
    private var lastPersistedCombined = -1f
    private var lastPhone = ""

    fun cognitiveEngine(): CognitiveStressEngine = cognitive

    @Synchronized
    fun update(phoneNumber: String, acoustic: Int, linguistic: Int, factual: Int, transcriptContext: String = "", cognitiveStressScore: Float = 0f): Snapshot {
        val cleanPhone = phoneNumber.trim()
        val adjusted = cognitive.applyToEvidence(acoustic.coerceIn(0,100)/100f, linguistic.coerceIn(0,100)/100f, cognitiveStressScore)
        val result = fusion.fuse(EvidenceFusionEngine.Streams(adjusted.first, adjusted.second, factual.coerceIn(0,100)/100f))
        if (cleanPhone != lastPhone) { lastPhone=cleanPhone; lastPersistAt=0L; lastPersistedCombined=-1f }
        val now=System.currentTimeMillis()
        val meaningful=result.combined>=0.65f||result.elevatedStreams>=2||result.factual>=0.75f
        val changed=lastPersistedCombined<0f||abs(result.combined-lastPersistedCombined)>=0.12f
        val shouldPersist=cleanPhone.isNotBlank()&&cleanPhone!="UNKNOWN CALLER"&&meaningful&&changed&&now-lastPersistAt>=15_000L
        if(shouldPersist){profiles.recordEvidence(cleanPhone,fusion.toProfileEvent(result,transcriptContext));lastPersistAt=now;lastPersistedCombined=result.combined}
        val snapshot=Snapshot(cleanPhone,(result.acoustic*100).toInt(),(result.linguistic*100).toInt(),(result.factual*100).toInt(),(result.combined*100).toInt(),result.elevatedStreams,shouldPersist,(cognitiveStressScore.coerceIn(0f,1f)*100).toInt(),result.logOdds,now,transcriptContext.trim().replace(Regex("\\s+"), " ").take(220))
        LiveSignalState.publish(snapshot)
        return snapshot
    }
}

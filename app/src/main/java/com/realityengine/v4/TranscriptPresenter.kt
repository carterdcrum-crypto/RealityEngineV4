package com.realityengine.v4

/** Pure formatter for the active-call transcript panel. */
object TranscriptPresenter {
    fun render(snapshot:LiveTranscriptState.State,route:AudioRouteState.Snapshot):String{
        val lines=snapshot.entries.takeLast(8).map{entry->val who=entry.isCaller?.let(::speakerLabel)?.let{"$it  // "}.orEmpty();"● $who${entry.text}"}.toMutableList()
        if(!snapshot.isFinal&&snapshot.text.isNotBlank()){val who=snapshot.isCaller?.let(::speakerLabel)?.let{"$it  // "}.orEmpty();lines+="○ $who${snapshot.text}"}
        if(lines.isNotEmpty())return lines.joinToString("\n")
        return if(route.updatedAtMs>0L)"○ CALL AUDIO // ${route.label}\n${route.detail}" else "AWAITING AUDIO STREAM…"
    }
    fun speakerLabel(isCaller:Boolean):String=if(isCaller)"CALLER" else "YOU"
}

package com.realityengine.v4

/** Pure review model for approving AI-learned caller memory one item at a time. */
object MemoryProposalReview {
    enum class Kind(val label: String) {
        LIKE("LIKE"),
        DISLIKE("DISLIKE"),
        FACT("FACT"),
        TOPIC("TOPIC"),
        STARTER("STARTER"),
        FOLLOW_UP("FOLLOW UP"),
        STYLE("STYLE"),
    }

    data class Item(val kind: Kind, val value: String)

    fun items(learned: CallerMemoryAiExtractor.Learned): List<Item> = buildList {
        learned.likes.forEach { add(Item(Kind.LIKE, it)) }
        learned.dislikes.forEach { add(Item(Kind.DISLIKE, it)) }
        learned.facts.forEach { add(Item(Kind.FACT, it)) }
        learned.topics.forEach { add(Item(Kind.TOPIC, it)) }
        learned.unresolved.forEach { add(Item(Kind.FOLLOW_UP, it)) }
        learned.starters.forEach { add(Item(Kind.STARTER, it)) }
        learned.preferredStyle.takeIf { it.isNotBlank() }?.let { add(Item(Kind.STYLE, it)) }
    }.filter { it.value.isNotBlank() }

    fun remove(
        learned: CallerMemoryAiExtractor.Learned,
        item: Item,
    ): CallerMemoryAiExtractor.Learned = when (item.kind) {
        Kind.LIKE -> learned.copy(likes = learned.likes.without(item.value))
        Kind.DISLIKE -> learned.copy(dislikes = learned.dislikes.without(item.value))
        Kind.FACT -> learned.copy(facts = learned.facts.without(item.value))
        Kind.TOPIC -> learned.copy(topics = learned.topics.without(item.value))
        Kind.STARTER -> learned.copy(starters = learned.starters.without(item.value))
        Kind.FOLLOW_UP -> learned.copy(unresolved = learned.unresolved.without(item.value))
        Kind.STYLE -> learned.copy(preferredStyle = "")
    }

    fun isEmpty(learned: CallerMemoryAiExtractor.Learned): Boolean = items(learned).isEmpty()

    private fun List<String>.without(value: String): List<String> {
        var removed = false
        return filterNot { candidate ->
            val match = !removed && candidate.equals(value, ignoreCase = true)
            if (match) removed = true
            match
        }
    }
}

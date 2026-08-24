package com.realityengine.v4

/** Pure navigation state for the first-launch walkthrough UI. */
class WalkthroughNavigator(
    private val stepCount: Int = WalkthroughContent.steps.size
) {
    init { require(stepCount > 0) }

    var index: Int = 0
        private set

    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == stepCount - 1
    val progressText: String get() = "${index + 1} of $stepCount"

    fun next(): Boolean {
        if (isLast) return false
        index++
        return true
    }

    fun previous(): Boolean {
        if (isFirst) return false
        index--
        return true
    }

    fun current(): WalkthroughContent.Step = WalkthroughContent.steps[index]
}

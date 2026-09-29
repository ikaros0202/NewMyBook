package com.xinyue.reader.feature.reader

enum class ReaderJumpReason { CHAPTER, SEARCH, BOOKMARK, ANNOTATION, PROGRESS }

data class ReaderJump(
    val fromOffset: Int,
    val targetOffset: Int,
    val reason: ReaderJumpReason,
)

class ReaderNavigationController(private val capacity: Int = 32) {
    init {
        require(capacity > 0) { "Navigation history capacity must be positive" }
    }

    var originOffset: Int? = null
        private set

    private val backStack = ArrayDeque<ReaderJump>()

    val historySize: Int get() = backStack.size
    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val isBrowsingTemporarily: Boolean get() = originOffset != null

    fun jump(from: Int, target: Int, reason: ReaderJumpReason): Int {
        if (from == target) return target
        if (originOffset == null) originOffset = from
        backStack.addLast(ReaderJump(from, target, reason))
        while (backStack.size > capacity) backStack.removeFirst()
        return target
    }

    fun back(): Int? {
        val target = backStack.removeLastOrNull()?.fromOffset ?: return null
        if (backStack.isEmpty() && target == originOffset) clear()
        return target
    }

    fun returnToOrigin(): Int? = originOffset.also { clear() }

    fun continueHere() = clear()

    fun clear() {
        originOffset = null
        backStack.clear()
    }
}

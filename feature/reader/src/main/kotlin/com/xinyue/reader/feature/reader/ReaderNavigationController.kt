package com.xinyue.reader.feature.reader

enum class ReaderJumpReason { CHAPTER, SEARCH, BOOKMARK, ANNOTATION, PROGRESS }

enum class ReaderNavigationMode {
    NONE,
    TEMPORARY,
    COMMITTED_CHAPTER,
}

data class ReaderJump(
    val fromOffset: Int,
    val targetOffset: Int,
    val reason: ReaderJumpReason,
)

data class ReaderNavigationTarget(
    val offset: Int,
    val commitProgress: Boolean,
)

class ReaderNavigationController(private val capacity: Int = 32) {
    init {
        require(capacity > 0) { "Navigation history capacity must be positive" }
    }

    private var committedOriginOffset: Int? = null
    private var temporaryOriginOffset: Int? = null

    private val temporaryBackStack = ArrayDeque<ReaderJump>()

    /** The current navigation origin, preferring an active temporary browsing session. */
    val originOffset: Int?
        get() = temporaryOriginOffset ?: committedOriginOffset

    val navigationMode: ReaderNavigationMode
        get() = when {
            temporaryOriginOffset != null -> ReaderNavigationMode.TEMPORARY
            committedOriginOffset != null -> ReaderNavigationMode.COMMITTED_CHAPTER
            else -> ReaderNavigationMode.NONE
        }

    val historySize: Int
        get() = temporaryBackStack.size + if (committedOriginOffset != null) 1 else 0

    val canGoBack: Boolean
        get() = temporaryBackStack.isNotEmpty() || committedOriginOffset != null

    val canReturnToOrigin: Boolean
        get() = temporaryOriginOffset != null || committedOriginOffset != null

    val isBrowsingTemporarily: Boolean
        get() = temporaryOriginOffset != null

    /**
     * Starts a temporary browsing jump. The jump is not a progress commit until
     * [continueHere] is called by the reader.
     */
    fun jump(from: Int, target: Int, reason: ReaderJumpReason): Int {
        return jumpTemporarily(from, target, reason)
    }

    fun jumpTemporarily(from: Int, target: Int, reason: ReaderJumpReason): Int {
        if (from == target) return target
        if (temporaryOriginOffset == null) temporaryOriginOffset = from
        temporaryBackStack.addLast(ReaderJump(from, target, reason))
        while (temporaryBackStack.size > capacity) temporaryBackStack.removeFirst()
        return target
    }

    /**
     * Records a chapter jump as the new stable reading position while retaining a return point.
     *
     * [returnOrigin] is the formal stable anchor captured before the selection. It can differ
     * from [from] when the chapter was selected while temporarily browsing a search or bookmark
     * result. Selecting the current temporary offset still ends temporary browsing and keeps the
     * formal origin.
     */
    fun commitChapterJump(from: Int, target: Int, returnOrigin: Int = from): Int {
        val wasBrowsingTemporarily = temporaryOriginOffset != null
        if (from == target && !wasBrowsingTemporarily) return target
        temporaryOriginOffset = null
        temporaryBackStack.clear()
        committedOriginOffset = returnOrigin
        return target
    }

    fun peekBack(): ReaderNavigationTarget? {
        temporaryBackStack.lastOrNull()?.let {
            return ReaderNavigationTarget(offset = it.fromOffset, commitProgress = false)
        }
        return committedOriginOffset?.let { ReaderNavigationTarget(offset = it, commitProgress = true) }
    }

    fun consumeBack(expected: ReaderNavigationTarget): Boolean {
        if (peekBack() != expected) return false
        if (temporaryBackStack.isNotEmpty()) {
            temporaryBackStack.removeLast()
            if (temporaryBackStack.isEmpty()) temporaryOriginOffset = null
        } else {
            committedOriginOffset = null
        }
        return true
    }

    fun peekReturnToOrigin(): ReaderNavigationTarget? {
        temporaryOriginOffset?.let {
            return ReaderNavigationTarget(offset = it, commitProgress = false)
        }
        return committedOriginOffset?.let { ReaderNavigationTarget(offset = it, commitProgress = true) }
    }

    fun consumeReturnToOrigin(expected: ReaderNavigationTarget): Boolean {
        if (peekReturnToOrigin() != expected) return false
        if (temporaryOriginOffset != null) {
            temporaryOriginOffset = null
            temporaryBackStack.clear()
        } else {
            committedOriginOffset = null
        }
        return true
    }

    fun back(): Int? {
        val target = peekBack() ?: return null
        return target.offset.takeIf { consumeBack(target) }
    }

    fun returnToOrigin(): Int? {
        val target = peekReturnToOrigin() ?: return null
        return target.offset.takeIf { consumeReturnToOrigin(target) }
    }

    /** Ends only the temporary browsing session, retaining a committed chapter return point. */
    fun continueHere() {
        temporaryOriginOffset = null
        temporaryBackStack.clear()
    }

    fun clear() {
        committedOriginOffset = null
        temporaryOriginOffset = null
        temporaryBackStack.clear()
    }
}

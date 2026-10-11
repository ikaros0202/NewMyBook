package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReaderNavigationControllerTest {
    @Test
    fun `first temporary jump captures origin and back resolves browsing`() {
        val controller = ReaderNavigationController()

        assertThat(controller.jump(100, 500, ReaderJumpReason.SEARCH)).isEqualTo(500)
        assertThat(controller.originOffset).isEqualTo(100)
        assertThat(controller.isBrowsingTemporarily).isTrue()
        assertThat(controller.back()).isEqualTo(100)
        assertThat(controller.isBrowsingTemporarily).isFalse()
    }

    @Test
    fun `later jumps unwind and same offset does not duplicate`() {
        val controller = ReaderNavigationController()
        controller.jump(100, 500, ReaderJumpReason.CHAPTER)
        controller.jump(500, 900, ReaderJumpReason.BOOKMARK)
        controller.jump(900, 900, ReaderJumpReason.SEARCH)

        assertThat(controller.historySize).isEqualTo(2)
        assertThat(controller.back()).isEqualTo(500)
        assertThat(controller.returnToOrigin()).isEqualTo(100)
        assertThat(controller.isBrowsingTemporarily).isFalse()
    }

    @Test
    fun `continue clears origin and history while bounded capacity keeps newest jumps`() {
        val controller = ReaderNavigationController(capacity = 32)
        repeat(40) { index ->
            controller.jump(index, index + 1, ReaderJumpReason.PROGRESS)
        }
        assertThat(controller.historySize).isEqualTo(32)
        assertThat(controller.originOffset).isEqualTo(0)

        controller.continueHere()

        assertThat(controller.historySize).isEqualTo(0)
        assertThat(controller.originOffset).isNull()
    }

    @Test
    fun `chapter jump is committed while retaining a progress committing return point`() {
        val controller = ReaderNavigationController()

        controller.commitChapterJump(from = 100, target = 900)

        assertThat(controller.navigationMode).isEqualTo(ReaderNavigationMode.COMMITTED_CHAPTER)
        assertThat(controller.isBrowsingTemporarily).isFalse()
        assertThat(controller.canReturnToOrigin).isTrue()
        assertThat(controller.peekReturnToOrigin()).isEqualTo(
            ReaderNavigationTarget(offset = 100, commitProgress = true),
        )

        val target = requireNotNull(controller.peekReturnToOrigin())
        assertThat(controller.consumeReturnToOrigin(target)).isTrue()
        assertThat(controller.canReturnToOrigin).isFalse()
        assertThat(controller.navigationMode).isEqualTo(ReaderNavigationMode.NONE)
    }

    @Test
    fun `temporary browsing from a committed chapter returns to chapter while retaining chapter origin`() {
        val controller = ReaderNavigationController()

        controller.commitChapterJump(from = 100, target = 900)
        controller.jumpTemporarily(from = 900, target = 1_200, reason = ReaderJumpReason.SEARCH)

        assertThat(controller.navigationMode).isEqualTo(ReaderNavigationMode.TEMPORARY)
        assertThat(controller.peekReturnToOrigin()).isEqualTo(
            ReaderNavigationTarget(offset = 900, commitProgress = false),
        )
        val temporaryOrigin = requireNotNull(controller.peekReturnToOrigin())
        assertThat(controller.consumeReturnToOrigin(temporaryOrigin)).isTrue()
        assertThat(controller.navigationMode).isEqualTo(ReaderNavigationMode.COMMITTED_CHAPTER)
        assertThat(controller.peekReturnToOrigin()).isEqualTo(
            ReaderNavigationTarget(offset = 100, commitProgress = true),
        )
    }

    @Test
    fun `chapter commit from temporary browsing keeps the formal return origin`() {
        val controller = ReaderNavigationController()

        controller.jumpTemporarily(from = 100, target = 500, reason = ReaderJumpReason.SEARCH)
        controller.commitChapterJump(from = 500, target = 900, returnOrigin = 100)

        assertThat(controller.isBrowsingTemporarily).isFalse()
        assertThat(controller.peekReturnToOrigin()).isEqualTo(
            ReaderNavigationTarget(offset = 100, commitProgress = true),
        )
    }

    @Test
    fun `same offset chapter commit ends temporary browsing and keeps the formal return origin`() {
        val controller = ReaderNavigationController()

        controller.jumpTemporarily(from = 100, target = 500, reason = ReaderJumpReason.BOOKMARK)
        controller.commitChapterJump(from = 500, target = 500, returnOrigin = 100)

        assertThat(controller.isBrowsingTemporarily).isFalse()
        assertThat(controller.peekReturnToOrigin()).isEqualTo(
            ReaderNavigationTarget(offset = 100, commitProgress = true),
        )
    }
}

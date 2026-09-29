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
}

package com.xinyue.reader.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ReleaseSelectionTest {
    @Test
    fun nonDebuggableReleaseLikeTargetSupportsCompleteSelectionJourney() {
        ReleaseSelectionJourney().run {
            resetAndLaunch()
            assertNonDebuggableTarget()
            importAndOpenPublicFixture()
            assertSelectionSuppressesAndRestoresPaging()
            createAllHighlightColors()
            createEditAndJumpToNote()
        }
    }
}

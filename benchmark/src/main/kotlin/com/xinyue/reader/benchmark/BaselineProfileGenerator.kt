package com.xinyue.reader.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        XinYueJourneys(this).coldStartToLibrary()
    }

    @Test
    fun criticalUserJourneys() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = false,
    ) {
        XinYueJourneys(this).apply {
            coldStartToLibrary()
            ensurePublicFixtureImported()
            openFixture()
            turnPages(10)
            openSettingsAndApplyReflow()
            openSearchAndFind("雨夜")
            returnToLibraryAndScroll()
        }
    }
}

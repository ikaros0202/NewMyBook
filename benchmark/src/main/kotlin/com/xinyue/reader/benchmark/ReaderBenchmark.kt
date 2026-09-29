package com.xinyue.reader.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class ReaderBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun pageTurnsWithoutCompilation() = benchmarkReader(
        compilationMode = CompilationMode.None(),
        measureBlock = { turnPages(10) },
    )

    @Test
    fun pageTurnsWithBaselineProfile() = benchmarkReader(
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        measureBlock = { turnPages(10) },
    )

    @Test
    fun readerReflowWithBaselineProfile() = benchmarkReader(
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        measureBlock = { openSettingsAndApplyReflow() },
    )

    @Test
    fun searchWithBaselineProfile() = benchmarkReader(
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        measureBlock = { openSearchAndFind("雨夜") },
    )

    @Test
    fun libraryScrollWithBaselineProfile() = benchmarkReader(
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        measureBlock = { returnToLibraryAndScroll() },
    )

    private fun benchmarkReader(
        compilationMode: CompilationMode,
        measureBlock: XinYueJourneys.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            setupBlock = { prepareReader() },
            measureBlock = { XinYueJourneys(this).measureBlock() },
        )
    }

    private fun MacrobenchmarkScope.prepareReader() {
        XinYueJourneys(this).apply {
            coldStartToLibrary()
            ensurePublicFixtureImported()
            openFixture()
        }
    }
}

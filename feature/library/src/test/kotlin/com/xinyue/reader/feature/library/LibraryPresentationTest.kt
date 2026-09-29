package com.xinyue.reader.feature.library

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.LibraryGridDensity
import org.junit.Test

class LibraryPresentationTest {
    @Test
    fun `empty shelf uses inline import while an existing shelf uses the top bar`() {
        assertThat(primaryImportPlacement(hasAnyBooks = false)).isEqualTo(PrimaryImportPlacement.INLINE_EMPTY)
        assertThat(primaryImportPlacement(hasAnyBooks = true)).isEqualTo(PrimaryImportPlacement.TOP_BAR)
    }

    @Test
    fun `cover grid exposes two three and four density columns on a standard compact phone`() {
        assertThat(libraryGridColumns(360f, 1f, LibraryGridDensity.COMFORTABLE)).isEqualTo(2)
        assertThat(libraryGridColumns(360f, 1f, LibraryGridDensity.STANDARD)).isEqualTo(3)
        assertThat(libraryGridColumns(360f, 1f, LibraryGridDensity.COMPACT)).isEqualTo(4)
    }

    @Test
    fun `cover grid reduces columns for narrow screens and large text`() {
        assertThat(libraryGridColumns(320f, 1f, LibraryGridDensity.COMPACT)).isEqualTo(3)
        assertThat(libraryGridColumns(360f, 1.5f, LibraryGridDensity.STANDARD)).isEqualTo(2)
        assertThat(libraryGridColumns(600f, 2f, LibraryGridDensity.COMPACT)).isEqualTo(2)
    }

    @Test
    fun `cover grid grows gradually on wider layouts`() {
        assertThat(libraryGridColumns(600f, 1f, LibraryGridDensity.STANDARD)).isEqualTo(4)
        assertThat(libraryGridColumns(900f, 1f, LibraryGridDensity.STANDARD)).isEqualTo(5)
    }
}

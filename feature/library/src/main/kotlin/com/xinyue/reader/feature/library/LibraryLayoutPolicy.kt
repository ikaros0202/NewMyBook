package com.xinyue.reader.feature.library

import com.xinyue.reader.core.domain.model.LibraryGridDensity

internal fun libraryGridColumns(
    widthDp: Float,
    fontScale: Float,
    density: LibraryGridDensity,
): Int {
    if (fontScale >= 2f) return 2

    val baseColumns = when {
        widthDp < 340f -> when (density) {
            LibraryGridDensity.COMFORTABLE -> 2
            LibraryGridDensity.STANDARD,
            LibraryGridDensity.COMPACT,
            -> 3
        }

        widthDp < 600f -> when (density) {
            LibraryGridDensity.COMFORTABLE -> 2
            LibraryGridDensity.STANDARD -> 3
            LibraryGridDensity.COMPACT -> 4
        }

        widthDp < 840f -> when (density) {
            LibraryGridDensity.COMFORTABLE -> 3
            LibraryGridDensity.STANDARD -> 4
            LibraryGridDensity.COMPACT -> 5
        }

        else -> when (density) {
            LibraryGridDensity.COMFORTABLE -> 4
            LibraryGridDensity.STANDARD -> 5
            LibraryGridDensity.COMPACT -> 6
        }
    }
    return if (fontScale >= 1.5f) (baseColumns - 1).coerceAtLeast(2) else baseColumns
}

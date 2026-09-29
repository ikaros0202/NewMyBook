package com.xinyue.reader.core.domain.model

/** The two presentation modes available for the local library. */
enum class LibraryLayoutMode {
    COVER_GRID,
    COMPACT_LIST,
}

/** Controls how many books are visible in the cover grid. */
enum class LibraryGridDensity {
    COMFORTABLE,
    STANDARD,
    COMPACT,
}

/** Device-local library presentation preferences; this is not book data. */
data class LibraryLayoutPreference(
    val mode: LibraryLayoutMode = LibraryLayoutMode.COVER_GRID,
    val gridDensity: LibraryGridDensity = LibraryGridDensity.STANDARD,
)

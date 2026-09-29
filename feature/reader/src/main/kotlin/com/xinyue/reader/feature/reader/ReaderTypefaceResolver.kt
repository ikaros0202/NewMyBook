package com.xinyue.reader.feature.reader

import android.graphics.Typeface
import android.os.Build
import com.xinyue.reader.core.data.FontFileWarningReporter
import com.xinyue.reader.core.data.ImportedFontFileStore
import com.xinyue.reader.core.domain.model.ReaderFontRef
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReaderTypefaceResolver private constructor(
    private val openImportedFont: (String) -> File?,
    private val reportFailure: (String) -> Unit,
) {
    @Inject
    constructor(
        importedFontFileStore: ImportedFontFileStore,
        warningReporter: FontFileWarningReporter,
    ) : this(importedFontFileStore::open, warningReporter::warn)

    private val cache = ConcurrentHashMap<CacheKey, Typeface>()

    fun resolve(font: ReaderFontRef, weight: Int): Typeface {
        val key = CacheKey(font, weight.coerceIn(100, 900))
        cache[key]?.let { return it }
        val resolved = resolveUncached(key)
        return cache.putIfAbsent(key, resolved) ?: resolved
    }

    private fun resolveUncached(key: CacheKey): Typeface {
        val base = when (val font = key.font) {
            ReaderFontRef.System -> Typeface.DEFAULT
            ReaderFontRef.Serif -> Typeface.SERIF
            ReaderFontRef.SansSerif -> Typeface.SANS_SERIF
            is ReaderFontRef.Imported -> {
                val file = try {
                    openImportedFont(font.fontId)
                } catch (failure: Exception) {
                    reportFailure(font.fontId)
                    null
                }
                if (file == null) return Typeface.DEFAULT
                try {
                    Typeface.createFromFile(file)
                } catch (failure: RuntimeException) {
                    reportFailure(font.fontId)
                    return Typeface.DEFAULT
                }
            }
        }
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                Typeface.create(base, key.weight, false)
            } else {
                Typeface.create(base, if (key.weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            }
        } catch (failure: RuntimeException) {
            (key.font as? ReaderFontRef.Imported)?.let { reportFailure(it.fontId) }
            Typeface.DEFAULT
        }
    }

    private data class CacheKey(val font: ReaderFontRef, val weight: Int)

    companion object {
        internal fun forTests(
            openImportedFont: (String) -> File? = { null },
            reportFailure: (String) -> Unit = {},
        ): ReaderTypefaceResolver = ReaderTypefaceResolver(openImportedFont, reportFailure)
    }
}

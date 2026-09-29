package com.xinyue.reader.core.data

import com.xinyue.reader.core.database.dao.ReaderSettingsDao
import com.xinyue.reader.core.database.dao.ReaderThemeDao
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderSettingsOverrides
import javax.inject.Inject
import kotlinx.serialization.json.Json

interface FontReferenceCounter {
    suspend fun count(fontId: String): Int
}

class ImportedFontReferenceCounter @Inject constructor(
    private val settingsDao: ReaderSettingsDao,
    private val themeDao: ReaderThemeDao,
    private val json: Json,
) : FontReferenceCounter {
    override suspend fun count(fontId: String): Int {
        var count = settingsDao.getGlobal()
            ?.let { json.decodeOrDefault(it.settingsJson, ReaderSettings()).font.importedId() }
            ?.let { if (it == fontId) 1 else 0 }
            ?: 0
        count += settingsDao.getAllBookOverrides().count { entity ->
            json.decodeOrDefault(entity.overridesJson, ReaderSettingsOverrides())
                .font.importedId() == fontId
        }
        count += themeDao.getAll().count { entity ->
            json.decodeOrDefault(entity.settingsJson, ReaderSettings()).font.importedId() == fontId
        }
        return count
    }

    private fun ReaderFontRef?.importedId(): String? = (this as? ReaderFontRef.Imported)?.fontId
}

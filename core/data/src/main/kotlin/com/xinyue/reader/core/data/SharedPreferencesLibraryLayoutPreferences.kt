package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.domain.model.LibraryGridDensity
import com.xinyue.reader.core.domain.model.LibraryLayoutMode
import com.xinyue.reader.core.domain.model.LibraryLayoutPreference
import com.xinyue.reader.core.domain.repository.LibraryLayoutPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SharedPreferences-backed device-local library layout state. */
@Singleton
class SharedPreferencesLibraryLayoutPreferences @Inject constructor(
    @ApplicationContext context: Context,
) : LibraryLayoutPreferences {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(readPreference())

    override fun observe(): Flow<LibraryLayoutPreference> = state.asStateFlow()

    override suspend fun set(preference: LibraryLayoutPreference) {
        val committed = withContext(Dispatchers.IO) {
            preferences.edit()
                .putString(KEY_MODE, preference.mode.name)
                .putString(KEY_GRID_DENSITY, preference.gridDensity.name)
                .commit()
        }
        check(committed) { "Unable to persist library layout preferences" }
        state.value = preference
    }

    private fun readPreference(): LibraryLayoutPreference = LibraryLayoutPreference(
        mode = preferences.getString(KEY_MODE, null)
            ?.let { stored -> LibraryLayoutMode.entries.firstOrNull { it.name == stored } }
            ?: LibraryLayoutMode.COVER_GRID,
        gridDensity = preferences.getString(KEY_GRID_DENSITY, null)
            ?.let { stored -> LibraryGridDensity.entries.firstOrNull { it.name == stored } }
            ?: LibraryGridDensity.STANDARD,
    )

    private companion object {
        const val PREFERENCES_NAME = "library_layout_preferences"
        const val KEY_MODE = "layout_mode"
        const val KEY_GRID_DENSITY = "grid_density"
    }
}

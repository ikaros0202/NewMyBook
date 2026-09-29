package com.xinyue.reader.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.LibraryGridDensity
import com.xinyue.reader.core.domain.model.LibraryLayoutMode
import com.xinyue.reader.core.domain.model.LibraryLayoutPreference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesLibraryLayoutPreferencesTest {
    @Test
    fun `defaults to standard cover grid`() = runTest {
        clearPreferences()

        val preferences = SharedPreferencesLibraryLayoutPreferences(applicationContext())

        assertThat(preferences.observe().first()).isEqualTo(LibraryLayoutPreference())
    }

    @Test
    fun `persists mode and density independently`() = runTest {
        clearPreferences()
        val preferences = SharedPreferencesLibraryLayoutPreferences(applicationContext())
        val selected = LibraryLayoutPreference(
            mode = LibraryLayoutMode.COMPACT_LIST,
            gridDensity = LibraryGridDensity.COMPACT,
        )

        preferences.set(selected)

        assertThat(preferences.observe().first()).isEqualTo(selected)
        assertThat(
            SharedPreferencesLibraryLayoutPreferences(applicationContext()).observe().first(),
        ).isEqualTo(selected)
    }

    @Test
    fun `unknown stored values fall back to safe defaults per field`() = runTest {
        clearPreferences()
        applicationContext().getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit()
            .putString(KEY_MODE, "UNKNOWN_MODE")
            .putString(KEY_GRID_DENSITY, "UNKNOWN_DENSITY")
            .commit()

        val preferences = SharedPreferencesLibraryLayoutPreferences(applicationContext())

        assertThat(preferences.observe().first()).isEqualTo(
            LibraryLayoutPreference(),
        )
    }

    private fun applicationContext(): Context =
        ApplicationProvider.getApplicationContext()

    private fun clearPreferences() {
        applicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "library_layout_preferences"
        const val KEY_MODE = "layout_mode"
        const val KEY_GRID_DENSITY = "grid_density"
    }
}

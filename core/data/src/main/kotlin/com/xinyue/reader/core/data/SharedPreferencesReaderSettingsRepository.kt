package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.domain.model.ReaderColorTheme
import com.xinyue.reader.core.domain.model.ReaderFontRef
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderTapAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only compatibility adapter for settings written before schema 10. */
@Singleton
class LegacyReaderSettingsReader @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun readSettings(): ReaderSettings = ReaderSettings(
        fontSizeSp = preferences.getFloat(KEY_FONT_SIZE, 20f),
        lineHeightMultiplier = preferences.getFloat(KEY_LINE_HEIGHT, 1.6f),
        horizontalPaddingDp = preferences.getInt(KEY_HORIZONTAL_PADDING, 24),
        colorTheme = preferences.getString(KEY_COLOR_THEME, null)
            ?.let { name -> ReaderColorTheme.entries.firstOrNull { it.name == name } }
            ?: ReaderColorTheme.PAPER,
        brightness = preferences.getFloat(KEY_BRIGHTNESS, -1f),
        font = when (preferences.getString(KEY_FONT_FAMILY, null)) {
            "SERIF" -> ReaderFontRef.Serif
            "SANS_SERIF" -> ReaderFontRef.SansSerif
            else -> ReaderFontRef.System
        },
        pageAnimation = preferences.getString(KEY_PAGE_ANIMATION, null)
            ?.let { name ->
                if (name == "FADE") ReaderPageAnimation.COVER
                else ReaderPageAnimation.entries.firstOrNull { it.name == name }
            }
            ?: ReaderPageAnimation.SLIDE,
        keepScreenOn = preferences.getBoolean(KEY_KEEP_SCREEN_ON, true),
        volumeKeyPageTurn = preferences.getBoolean(KEY_VOLUME_KEY_PAGE_TURN, true),
        showBookTitle = preferences.getBoolean(KEY_SHOW_BOOK_TITLE, false),
        showChapterTitle = preferences.getBoolean(KEY_SHOW_CHAPTER_TITLE, true),
        showPageNumber = preferences.getBoolean(KEY_SHOW_PAGE_NUMBER, false),
        showBookProgress = preferences.getBoolean(KEY_SHOW_BOOK_PROGRESS, true),
        showChapterProgress = preferences.getBoolean(KEY_SHOW_CHAPTER_PROGRESS, false),
        showClock = preferences.getBoolean(KEY_SHOW_CLOCK, false),
        showBattery = preferences.getBoolean(KEY_SHOW_BATTERY, false),
        tapZoneActions = preferences.getString(KEY_TAP_ZONE_ACTIONS, null)
            ?.split(',')
            ?.mapNotNull { name -> ReaderTapAction.entries.firstOrNull { it.name == name } }
            ?: ReaderSettings.DEFAULT_TAP_ZONE_ACTIONS,
    ).normalized()

    fun isMigrationComplete(): Boolean = preferences.getBoolean(KEY_ROOM_MIGRATION_COMPLETE, false)

    fun markMigrationComplete() {
        check(preferences.edit().putBoolean(KEY_ROOM_MIGRATION_COMPLETE, true).commit()) {
            "Unable to persist legacy reader settings migration marker"
        }
    }

    internal fun clearForTest() {
        preferences.edit().clear().commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "reader_settings"
        const val KEY_FONT_SIZE = "font_size_sp"
        const val KEY_LINE_HEIGHT = "line_height_multiplier"
        const val KEY_HORIZONTAL_PADDING = "horizontal_padding_dp"
        const val KEY_COLOR_THEME = "color_theme"
        const val KEY_BRIGHTNESS = "brightness"
        const val KEY_FONT_FAMILY = "font_family"
        const val KEY_PAGE_ANIMATION = "page_animation"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_VOLUME_KEY_PAGE_TURN = "volume_key_page_turn"
        const val KEY_SHOW_BOOK_TITLE = "show_book_title"
        const val KEY_SHOW_CHAPTER_TITLE = "show_chapter_title"
        const val KEY_SHOW_PAGE_NUMBER = "show_page_number"
        const val KEY_SHOW_BOOK_PROGRESS = "show_book_progress"
        const val KEY_SHOW_CHAPTER_PROGRESS = "show_chapter_progress"
        const val KEY_SHOW_CLOCK = "show_clock"
        const val KEY_SHOW_BATTERY = "show_battery"
        const val KEY_TAP_ZONE_ACTIONS = "tap_zone_actions"
        const val KEY_ROOM_MIGRATION_COMPLETE = "v1_2_room_migration_complete"
    }
}

package com.xinyue.reader.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeScheduleMode {
    OFF,
    FOLLOW_SYSTEM,
    FIXED_TIME,
}

@Serializable
data class ReaderThemeManualOverride(
    val themeId: String,
    val automaticThemeIdAtActivation: String,
    val expiresAtEpochMillis: Long? = null,
)

@Serializable
data class ReaderThemeSchedule(
    val mode: ThemeScheduleMode = ThemeScheduleMode.OFF,
    val lightThemeId: String = BUILT_IN_PAPER_ID,
    val darkThemeId: String = BUILT_IN_DARK_ID,
    val lightMinuteOfDay: Int = 7 * 60,
    val darkMinuteOfDay: Int = 22 * 60,
    val manualOverrideUntilNextSwitch: Boolean = true,
) {
    fun resolve(
        nowMinuteOfDay: Int,
        systemDark: Boolean,
        presets: Collection<ReaderThemePreset>,
        manualOverride: ReaderThemeManualOverride? = null,
    ): String {
        val safeNow = nowMinuteOfDay.coerceIn(FIRST_MINUTE, LAST_MINUTE)
        val availableIds = presets.asSequence()
            .map(ReaderThemePreset::id)
            .filter(String::isNotBlank)
            .toSet()
        val wantsDark = when (mode) {
            ThemeScheduleMode.OFF -> false
            ThemeScheduleMode.FOLLOW_SYSTEM -> systemDark
            ThemeScheduleMode.FIXED_TIME -> !isLightInterval(safeNow)
        }
        val automatic = resolveAvailable(
            requestedId = if (wantsDark) darkThemeId else lightThemeId,
            fallbackId = if (wantsDark) BUILT_IN_DARK_ID else BUILT_IN_PAPER_ID,
            availableIds = availableIds,
        )
        val safeOverride = manualOverride
            ?.takeIf { manualOverrideUntilNextSwitch }
            ?.takeIf { it.automaticThemeIdAtActivation == automatic }
            ?.themeId
            ?.let { resolveAvailable(it, automatic, availableIds) }
        return safeOverride ?: automatic
    }

    private fun isLightInterval(nowMinuteOfDay: Int): Boolean {
        val light = lightMinuteOfDay.coerceIn(FIRST_MINUTE, LAST_MINUTE)
        val dark = darkMinuteOfDay.coerceIn(FIRST_MINUTE, LAST_MINUTE)
        return when {
            light == dark -> true
            light < dark -> nowMinuteOfDay in light until dark
            else -> nowMinuteOfDay >= light || nowMinuteOfDay < dark
        }
    }

    private fun resolveAvailable(
        requestedId: String,
        fallbackId: String,
        availableIds: Set<String>,
    ): String = requestedId.takeIf {
        it == BUILT_IN_PAPER_ID || it == BUILT_IN_DARK_ID || it in availableIds
    } ?: fallbackId

    companion object {
        private const val FIRST_MINUTE = 0
        private const val LAST_MINUTE = 24 * 60 - 1
    }
}

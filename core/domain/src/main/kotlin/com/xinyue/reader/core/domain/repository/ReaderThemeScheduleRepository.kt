package com.xinyue.reader.core.domain.repository

import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import kotlinx.coroutines.flow.Flow

/**
 * Stores automatic theme selection and its optional temporary manual override in one coordinated row.
 * Updates must not overwrite unrelated global reader settings held in that row.
 */
interface ReaderThemeScheduleRepository {
    /** Observes the current automatic selection policy. */
    fun observeSchedule(): Flow<ReaderThemeSchedule>

    /** Observes the current manual override, or `null` when automatic selection is authoritative. */
    fun observeManualOverride(): Flow<ReaderThemeManualOverride?>

    /** Replaces only the schedule portion of the coordinated setting. */
    suspend fun updateSchedule(schedule: ReaderThemeSchedule)

    /** Replaces or clears only the temporary manual-override portion. */
    suspend fun updateManualOverride(override: ReaderThemeManualOverride?)
}

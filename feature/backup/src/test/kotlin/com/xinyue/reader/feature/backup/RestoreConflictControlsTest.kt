package com.xinyue.reader.feature.backup

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.RestoreConflict
import com.xinyue.reader.core.domain.model.RestoreConflictChoice
import com.xinyue.reader.core.domain.model.RestoreConflictKind
import org.junit.Test

class RestoreConflictControlsTest {
    @Test
    fun `defaults are stable complete and convert to typed resolutions`() {
        val conflicts = listOf(
            conflict("settings", setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.USE_BACKUP), RestoreConflictChoice.KEEP_LOCAL),
            conflict("group", setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.RENAME_BACKUP), RestoreConflictChoice.KEEP_LOCAL),
        )
        val selections = defaultConflictSelections(conflicts)

        assertThat(selections.keys).containsExactly("settings", "group")
        assertThat(conflictSelectionsComplete(conflicts, selections)).isTrue()
        assertThat(toRestoreResolutions(conflicts, selections).map { it.conflictId })
            .containsExactly("settings", "group").inOrder()
    }

    @Test
    fun `rename is incomplete until nonblank unique value and invalid choices are rejected`() {
        val conflict = conflict(
            "group", setOf(RestoreConflictChoice.KEEP_LOCAL, RestoreConflictChoice.RENAME_BACKUP),
            RestoreConflictChoice.KEEP_LOCAL,
        )
        assertThat(
            conflictSelectionsComplete(
                listOf(conflict), mapOf("group" to RestoreConflictSelection(RestoreConflictChoice.RENAME_BACKUP, "  ")),
            ),
        ).isFalse()
        assertThat(
            conflictSelectionsComplete(
                listOf(conflict), mapOf("group" to RestoreConflictSelection(RestoreConflictChoice.RENAME_BACKUP, "备份分组")),
            ),
        ).isTrue()
        assertThat(
            conflictSelectionsComplete(
                listOf(conflict), mapOf("group" to RestoreConflictSelection(RestoreConflictChoice.USE_BACKUP)),
            ),
        ).isFalse()
    }

    private fun conflict(
        id: String,
        choices: Set<RestoreConflictChoice>,
        suggested: RestoreConflictChoice,
    ) = RestoreConflict(id, RestoreConflictKind.GROUP, "公开标签", choices, suggested)
}

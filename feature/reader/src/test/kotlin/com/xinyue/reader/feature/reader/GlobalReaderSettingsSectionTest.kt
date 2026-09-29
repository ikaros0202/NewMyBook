package com.xinyue.reader.feature.reader

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.domain.model.ReaderPageAnimation
import com.xinyue.reader.core.domain.model.ReaderSettings
import org.junit.Test

class GlobalReaderSettingsSectionTest {
    @Test
    fun `section merge changes only fields owned by that section`() {
        val latest = ReaderSettings(fontSizeSp = 18f, keepScreenOn = false, showClock = true)
        val edited = ReaderSettings(fontSizeSp = 30f, keepScreenOn = true, showClock = false, pageAnimation = ReaderPageAnimation.NONE)

        val appearance = mergeGlobalReaderSettings(GlobalReaderSettingsSection.APPEARANCE, latest, edited)
        assertThat(appearance.fontSizeSp).isEqualTo(30f)
        assertThat(appearance.keepScreenOn).isFalse()
        assertThat(appearance.showClock).isTrue()

        val behavior = mergeGlobalReaderSettings(GlobalReaderSettingsSection.BEHAVIOR, latest, edited)
        assertThat(behavior.fontSizeSp).isEqualTo(18f)
        assertThat(behavior.keepScreenOn).isTrue()
        assertThat(behavior.pageAnimation).isEqualTo(ReaderPageAnimation.NONE)
        assertThat(behavior.showClock).isTrue()
    }
}

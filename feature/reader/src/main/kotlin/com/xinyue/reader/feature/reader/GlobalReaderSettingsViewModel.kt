package com.xinyue.reader.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinyue.reader.core.data.ImportSourceFactory
import com.xinyue.reader.core.domain.model.FontRemovalResult
import com.xinyue.reader.core.domain.model.ImportedFont
import com.xinyue.reader.core.domain.model.ReaderSettings
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.repository.ImportedFontRepository
import com.xinyue.reader.core.domain.repository.ReaderSettingsRepository
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GlobalReaderSettingsUiState(
    val original: ReaderSettings = ReaderSettings(),
    val draft: ReaderSettings = ReaderSettings(),
    val importedFonts: List<ImportedFont> = emptyList(),
    val themes: List<ReaderThemePreset> = ReaderThemeManager.BUILT_IN_THEMES,
    val schedule: ReaderThemeSchedule = ReaderThemeSchedule(),
    val manualOverride: ReaderThemeManualOverride? = null,
    val saving: Boolean = false,
    val errorMessage: String? = null,
) {
    val isDirty: Boolean get() = draft != original
}

@HiltViewModel
class GlobalReaderSettingsViewModel @Inject constructor(
    private val settingsRepository: ReaderSettingsRepository,
    private val importedFontRepository: ImportedFontRepository,
    private val importSourceFactory: ImportSourceFactory,
    private val themeManager: ReaderThemeManager,
    private val scheduleRepository: ReaderThemeScheduleRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(GlobalReaderSettingsUiState())
    val uiState: StateFlow<GlobalReaderSettingsUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observe(null).collect { settings ->
                mutableUiState.update { state ->
                    if (state.isDirty) state.copy(original = settings) else state.copy(original = settings, draft = settings)
                }
            }
        }
        viewModelScope.launch {
            importedFontRepository.observeAll().collect { fonts -> mutableUiState.update { it.copy(importedFonts = fonts) } }
        }
        viewModelScope.launch {
            themeManager.observeThemes().collect { themes -> mutableUiState.update { it.copy(themes = themes) } }
        }
        viewModelScope.launch {
            scheduleRepository.observeSchedule().collect { schedule -> mutableUiState.update { it.copy(schedule = schedule) } }
        }
        viewModelScope.launch {
            scheduleRepository.observeManualOverride().collect { manual -> mutableUiState.update { it.copy(manualOverride = manual) } }
        }
    }

    fun preview(settings: ReaderSettings) = mutableUiState.update { it.copy(draft = settings.normalized()) }

    fun cancel() = mutableUiState.update { it.copy(draft = it.original, errorMessage = null) }

    fun dismissError() = mutableUiState.update { it.copy(errorMessage = null) }

    fun save(section: GlobalReaderSettingsSection, onSaved: () -> Unit) {
        val edited = uiState.value.draft
        viewModelScope.launch {
            mutableUiState.update { it.copy(saving = true, errorMessage = null) }
            runCatching {
                settingsRepository.updateGlobalFromLatest { latest ->
                    mergeGlobalReaderSettings(section, latest, edited)
                }
            }.onSuccess {
                mutableUiState.update { it.copy(saving = false, original = it.draft) }
                onSaved()
            }.onFailure { failure ->
                mutableUiState.update { it.copy(saving = false, errorMessage = "保存失败") }
            }
        }
    }

    fun applyTheme(themeId: String) = runManagement("应用主题失败") {
        val theme = themeManager.find(themeId)
        settingsRepository.updateGlobalFromLatest { latest ->
            latest.applyAppearanceFrom(theme.settings)
        }
    }

    fun createTheme(name: String) = runManagement("创建主题失败") { themeManager.create(name, uiState.value.draft) }
    fun copyTheme(themeId: String, name: String) = runManagement("复制主题失败") { themeManager.copy(themeId, name) }
    fun renameTheme(themeId: String, name: String) = runManagement("重命名主题失败") { themeManager.rename(themeId, name) }
    fun updateTheme(themeId: String) = runManagement("更新主题失败") { themeManager.update(themeId, uiState.value.draft) }
    fun deleteTheme(themeId: String) = runManagement("删除主题失败") { themeManager.delete(themeId) }

    fun updateSchedule(schedule: ReaderThemeSchedule) = runManagement("更新自动切换失败") {
        scheduleRepository.updateSchedule(schedule)
        if (schedule.mode == com.xinyue.reader.core.domain.model.ThemeScheduleMode.OFF || !schedule.manualOverrideUntilNextSwitch) {
            scheduleRepository.updateManualOverride(null)
        }
    }

    fun importFont(uri: String) = runManagement("导入字体失败") {
        importedFontRepository.importFont(importSourceFactory.create(uri))
    }

    fun removeFont(fontId: String) = runManagement("删除字体失败") {
        when (val result = importedFontRepository.remove(fontId)) {
            FontRemovalResult.Removed, FontRemovalResult.NotFound -> Unit
            is FontRemovalResult.InUse -> error("字体仍被 ${result.referenceCount} 处设置使用")
        }
    }

    private fun runManagement(fallback: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                mutableUiState.update { it.copy(errorMessage = fallback) }
            }
        }
    }
}

package com.xinyue.reader.feature.reader

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import com.xinyue.reader.core.domain.model.BUILT_IN_DARK_ID
import com.xinyue.reader.core.domain.model.BUILT_IN_PAPER_ID
import com.xinyue.reader.core.domain.model.ReaderThemeManualOverride
import com.xinyue.reader.core.domain.model.ReaderThemePreset
import com.xinyue.reader.core.domain.model.ReaderThemeSchedule
import com.xinyue.reader.core.domain.model.ThemeScheduleMode
import com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository
import com.xinyue.reader.core.domain.time.EpochClock
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderThemeScheduleController private constructor(
    private val repository: ReaderThemeScheduleRepository,
    private val themes: Flow<List<ReaderThemePreset>>,
    private val systemDark: Flow<Boolean>,
    private val timeChanges: Flow<Unit>,
    private val clock: EpochClock,
    private val zoneIdProvider: () -> ZoneId,
    private val delayUntil: suspend (Long) -> Unit,
    @Suppress("unused") private val constructorMarker: Unit,
) {
    @Inject
    constructor(
        repository: ReaderThemeScheduleRepository,
        themeManager: ReaderThemeManager,
        clock: EpochClock,
        environment: ReaderSystemThemeEnvironment,
    ) : this(
        repository = repository,
        themes = themeManager.observeThemes(),
        systemDark = environment.systemDark,
        timeChanges = environment.timeChanges,
        clock = clock,
        zoneIdProvider = ZoneId::systemDefault,
        delayUntil = { delay(it) },
        constructorMarker = Unit,
    )

    internal constructor(
        repository: ReaderThemeScheduleRepository,
        themes: Flow<List<ReaderThemePreset>>,
        systemDark: Flow<Boolean>,
        timeChanges: Flow<Unit>,
        clock: EpochClock,
        zoneIdProvider: () -> ZoneId,
        delayUntil: suspend (Long) -> Unit,
    ) : this(
        repository,
        themes,
        systemDark,
        timeChanges,
        clock,
        zoneIdProvider,
        delayUntil,
        Unit,
    )

    private var job: Job? = null
    @Volatile
    private var latestRuntime: RuntimeInput? = null

    internal val isRunning: Boolean
        get() = job?.isActive == true
    internal var currentResolvedThemeId: String? = null
        private set

    fun start(scope: CoroutineScope, onThemeChanged: suspend (String?) -> Unit) {
        stop()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            decisions().collect { themeId ->
                onThemeChanged(themeId)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        latestRuntime = null
        currentResolvedThemeId = null
    }

    suspend fun updateSchedule(schedule: ReaderThemeSchedule) {
        val normalized = schedule.normalized()
        repository.updateSchedule(normalized)
        if (normalized.mode == ThemeScheduleMode.OFF || !normalized.manualOverrideUntilNextSwitch) {
            repository.updateManualOverride(null)
        }
    }

    suspend fun recordManualTheme(themeId: String) {
        val runtime = latestRuntime ?: return
        val schedule = runtime.schedule
        if (schedule.mode == ThemeScheduleMode.OFF || !schedule.manualOverrideUntilNextSwitch) {
            repository.updateManualOverride(null)
            return
        }
        val availableIds = runtime.themes.mapTo(mutableSetOf(), ReaderThemePreset::id)
        require(themeId in availableIds || themeId == BUILT_IN_PAPER_ID || themeId == BUILT_IN_DARK_ID) {
            "主题不存在"
        }
        val now = clock.nowEpochMillis()
        val automatic = resolveAutomatic(runtime, now)
        val expiry = if (schedule.mode == ThemeScheduleMode.FIXED_TIME) {
            nextBoundaryEpochMillis(schedule, now, zoneIdProvider())
        } else {
            null
        }
        repository.updateManualOverride(
            ReaderThemeManualOverride(
                themeId = themeId,
                automaticThemeIdAtActivation = automatic,
                expiresAtEpochMillis = expiry,
            ),
        )
    }

    private fun decisions(): Flow<String?> = combine(
        repository.observeSchedule(),
        repository.observeManualOverride(),
        themes,
        systemDark,
        timeChanges.onStart { emit(Unit) },
    ) { schedule, manual, presets, dark, _ ->
        RuntimeInput(schedule.normalized(), manual, presets, dark)
    }.flatMapLatest { runtime ->
        flow {
            latestRuntime = runtime
            if (runtime.schedule.mode == ThemeScheduleMode.OFF) {
                emit(null)
                awaitCancellation()
            }
            while (currentCoroutineContext().isActive) {
                val now = clock.nowEpochMillis()
                val automatic = resolveAutomatic(runtime, now)
                val manual = runtime.manualOverride
                val activeManual = manual?.takeIf { override ->
                    val beforeExpiry = override.expiresAtEpochMillis?.let(now::compareTo)?.let { it < 0 } ?: true
                    beforeExpiry && override.automaticThemeIdAtActivation == automatic
                }
                val resolved = activeManual?.themeId?.takeIf { requested ->
                    runtime.themes.any { it.id == requested } ||
                        requested == BUILT_IN_PAPER_ID || requested == BUILT_IN_DARK_ID
                } ?: automatic
                currentResolvedThemeId = resolved
                emit(resolved)
                if (manual != null && activeManual == null) {
                    repository.updateManualOverride(null)
                }
                val next = if (runtime.schedule.mode == ThemeScheduleMode.FIXED_TIME) {
                    nextBoundaryEpochMillis(runtime.schedule, now, zoneIdProvider())
                } else {
                    null
                }
                if (next == null) awaitCancellation()
                delayUntil((next - now).coerceAtLeast(1L))
            }
        }
    }.distinctUntilChanged()

    private fun resolveAutomatic(runtime: RuntimeInput, nowEpochMillis: Long): String {
        val local = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneIdProvider())
        return runtime.schedule.resolve(
            nowMinuteOfDay = local.hour * 60 + local.minute,
            systemDark = runtime.systemDark,
            presets = runtime.themes,
            manualOverride = null,
        )
    }

    private data class RuntimeInput(
        val schedule: ReaderThemeSchedule,
        val manualOverride: ReaderThemeManualOverride?,
        val themes: List<ReaderThemePreset>,
        val systemDark: Boolean,
    )
}

internal fun nextBoundaryEpochMillis(
    schedule: ReaderThemeSchedule,
    nowEpochMillis: Long,
    zoneId: ZoneId,
): Long? {
    if (schedule.mode != ThemeScheduleMode.FIXED_TIME) return null
    val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
    return listOf(schedule.lightMinuteOfDay, schedule.darkMinuteOfDay)
        .map(Int::coerceToMinuteOfDay)
        .map { minute ->
            var candidate = now.toLocalDate().atStartOfDay(zoneId).plusMinutes(minute.toLong())
            if (!candidate.toInstant().isAfter(now.toInstant())) candidate = candidate.plusDays(1)
            candidate.toInstant().toEpochMilli()
        }
        .minOrNull()
}

private fun ReaderThemeSchedule.normalized(): ReaderThemeSchedule = copy(
    lightThemeId = lightThemeId.trim().ifEmpty { BUILT_IN_PAPER_ID },
    darkThemeId = darkThemeId.trim().ifEmpty { BUILT_IN_DARK_ID },
    lightMinuteOfDay = lightMinuteOfDay.coerceToMinuteOfDay(),
    darkMinuteOfDay = darkMinuteOfDay.coerceToMinuteOfDay(),
)

private fun Int.coerceToMinuteOfDay(): Int = coerceIn(0, 24 * 60 - 1)

class ReaderSystemThemeEnvironment @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val systemDark: Flow<Boolean> = callbackFlow {
        fun Configuration.isDark(): Boolean =
            uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES

        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                trySend(newConfig.isDark())
            }

            @Deprecated("Android no longer dispatches this callback on modern devices")
            override fun onLowMemory() = Unit
        }
        context.registerComponentCallbacks(callbacks)
        trySend(context.resources.configuration.isDark())
        awaitClose { context.unregisterComponentCallbacks(callbacks) }
    }.distinctUntilChanged()

    val timeChanges: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
            },
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
    }
}

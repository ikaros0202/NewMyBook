# AndroidJUnitRunner resolves this from the target application classpath on API 26.
# Keep it only in the installable acceptance target; the Release AAB remains unchanged.
-keep class androidx.tracing.** { *; }

# The minified androidTest APK shares Kotlin runtime classes with its target APK.
# Keep the acceptance target's runtime complete so test-framework entry points do
# not resolve to classes removed as unused by the product-only shrink graph.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }

# Android instrumentation libraries deliberately share AndroidX runtime classes
# with the target APK. Preserve that target-side runtime in this acceptance-only
# artifact so UI-test-only entry points remain resolvable after R8.
-keep class androidx.** { *; }
-keep class javax.inject.** { *; }
-keep class dagger.hilt.android.EntryPointAccessors { *; }

# Acceptance instrumentation links these production contracts from a separately
# shrunk test APK. Preserve their binary names and members across both APKs.
-keep class com.xinyue.reader.core.data.** { *; }
-keep interface com.xinyue.reader.BackupDebugEntryPoint { *; }

# Accessibility screenshot instrumentation renders deterministic public models
# through production Compose entry points from the separately shrunk test APK.
# Keep only that cross-APK ABI stable; the shipping Release artifact does not use
# this acceptance-only rules file.
-keep class com.xinyue.reader.core.domain.model.** { *; }
-keep interface com.xinyue.reader.core.domain.repository.ReaderThemeScheduleRepository { *; }
-keep class com.xinyue.reader.feature.library.LibraryUiState { *; }
-keep class com.xinyue.reader.feature.library.LibraryScreenKt { *; }
-keep class com.xinyue.reader.feature.library.StatisticsUiState { *; }
-keep class com.xinyue.reader.feature.library.StatisticsPeriod { *; }
-keep class com.xinyue.reader.feature.library.DailyReadingStat { *; }
-keep class com.xinyue.reader.feature.library.BookReadingRank { *; }
-keep class com.xinyue.reader.feature.library.StatisticsScreenKt { *; }
-keep class com.xinyue.reader.feature.backup.RestoreConflictSelection { *; }
-keep class com.xinyue.reader.feature.backup.RestoreConflictControlsKt { *; }
-keep class com.xinyue.reader.feature.backup.RestorePreviewScreenKt { *; }

package com.xinyue.reader.core.data

import android.content.Context
import android.util.Log
import com.xinyue.reader.core.database.dao.ImportedFontDao
import com.xinyue.reader.core.database.entity.ImportedFontEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class ImportedFontFileStore internal constructor(
    private val filesRoot: File,
    dao: ImportedFontDao,
    private val scope: CoroutineScope,
    private val warningReporter: FontFileWarningReporter,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        dao: ImportedFontDao,
        warningReporter: FontFileWarningReporter,
    ) : this(
        context.filesDir,
        dao,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
        warningReporter,
    )

    internal constructor(
        filesRoot: File,
        dao: ImportedFontDao,
        scope: CoroutineScope,
    ) : this(filesRoot, dao, scope, FontFileWarningReporter { })

    private val relativePaths = ConcurrentHashMap<String, String>()
    private val warnedFontIds = ConcurrentHashMap.newKeySet<String>()

    init {
        scope.launch {
            dao.observeAll().collectLatest { entities ->
                relativePaths.clear()
                entities.forEach { relativePaths[it.id] = it.privateRelativePath }
            }
        }
    }

    fun open(fontId: String): File? {
        val relativePath = relativePaths[fontId] ?: return null
        val file = resolveSafeFontFile(relativePath)?.takeIf { it.isFile && it.canRead() }
        if (file == null && warnedFontIds.add(fontId)) warningReporter.warn(fontId)
        return file
    }

    internal fun record(entity: ImportedFontEntity) {
        relativePaths[entity.id] = entity.privateRelativePath
    }

    internal fun forget(fontId: String) {
        relativePaths.remove(fontId)
        warnedFontIds.remove(fontId)
    }

    private fun resolveSafeFontFile(relativePath: String): File? {
        val fontRoot = File(filesRoot, FONTS_DIRECTORY).canonicalFile
        val candidate = File(filesRoot, relativePath).canonicalFile
        return candidate.takeIf { it.toPath().startsWith(fontRoot.toPath()) }
    }

    companion object {
        internal const val FONTS_DIRECTORY = "fonts"
    }
}

fun interface FontFileWarningReporter {
    fun warn(fontId: String)
}

class AndroidFontFileWarningReporter @Inject constructor() : FontFileWarningReporter {
    override fun warn(fontId: String) {
        Log.w("XinYueFont", "导入字体不可用，已回退到系统字体")
    }
}

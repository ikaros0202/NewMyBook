package com.xinyue.reader.core.data

import com.xinyue.reader.core.text.TextContentInspector
import com.xinyue.reader.core.text.TxtEncodingAnalysis
import com.xinyue.reader.core.text.TxtEncodingAnalyzer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

data class ImportPreflightItem(
    val uriString: String,
    val displayName: String,
    val actualSizeBytes: Long?,
    val encoding: TxtEncodingAnalysis?,
    val errorMessage: String?,
) {
    val isValid: Boolean get() = encoding != null && errorMessage == null
}

interface ImportPreflightService {
    suspend fun analyze(uriStrings: List<String>): List<ImportPreflightItem>
}

@Singleton
class AndroidImportPreflightService @Inject constructor(
    private val sourceFactory: ImportSourceFactory,
) : ImportPreflightService {
    private val sampler = TextSegmentSampler()

    override suspend fun analyze(uriStrings: List<String>): List<ImportPreflightItem> = withContext(Dispatchers.IO) {
        uriStrings.distinct().map { uriString -> analyzeOne(uriString) }
    }

    private suspend fun analyzeOne(uriString: String): ImportPreflightItem {
        var displayName = uriString.substringAfterLast('/').ifBlank { "未命名小说.txt" }
        return try {
            val source = sourceFactory.create(uriString)
            displayName = source.displayName
            require(source.displayName.endsWith(".txt", ignoreCase = true)) { "请选择 TXT 文件" }
            val sampled = sampler.sample(source.openStream, source.sizeBytes)
            val encoding = TxtEncodingAnalyzer.analyze(sampled.segments)
            validatePreview(encoding)
            ImportPreflightItem(
                uriString = uriString,
                displayName = displayName,
                actualSizeBytes = sampled.actualSizeBytes,
                encoding = encoding,
                errorMessage = null,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            ImportPreflightItem(
                uriString = uriString,
                displayName = displayName,
                actualSizeBytes = null,
                encoding = null,
                errorMessage = error.message?.takeIf { it in SAFE_PREFLIGHT_MESSAGES }
                    ?: "无法检查该 TXT 文件",
            )
        }
    }

    private fun validatePreview(analysis: TxtEncodingAnalysis) {
        val candidate = analysis.candidates.first { it.charsetName == analysis.recommendedCharsetName }
        val inspector = TextContentInspector()
        sequenceOf(
            candidate.preview.beginning,
            candidate.preview.middle,
            candidate.preview.end,
        ).forEach { preview -> preview.forEach(inspector::accept) }
        inspector.requireReadableText()
    }

    private companion object {
        val SAFE_PREFLIGHT_MESSAGES = setOf(
            "请选择 TXT 文件",
            "TXT 文件必须介于 1 字节和 50 MB 之间",
            "TXT 文件不能为空",
            "TXT 文件不能超过 50 MB",
            "TXT 文件没有有效正文",
        )
    }
}

package com.xinyue.reader.feature.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xinyue.reader.core.data.DuplicateResolution
import com.xinyue.reader.core.data.BookManager
import com.xinyue.reader.core.data.ImportBatchState
import com.xinyue.reader.core.data.ImportItemState
import com.xinyue.reader.core.data.ImportPreflightItem
import com.xinyue.reader.core.data.ImportPreflightService
import com.xinyue.reader.core.data.ImportRequest
import com.xinyue.reader.core.data.ImportTaskScheduler
import com.xinyue.reader.core.domain.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val batch: ImportBatchState? = null,
    val isSubmitting: Boolean = false,
    val encodingDecision: ImportPreflightItem? = null,
    val preflightFailures: List<ImportPreflightItem> = emptyList(),
    val editingBook: Book? = null,
    val isSavingMetadata: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val scheduler: ImportTaskScheduler,
    private val preflightService: ImportPreflightService,
    private val bookManager: BookManager,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ImportUiState())
    val uiState = mutableUiState.asStateFlow()
    private var preflightItems = emptyList<ImportPreflightItem>()
    private val selectedCharsets = linkedMapOf<String, String>()

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            scheduler.latestBatch.collectLatest { batch ->
                mutableUiState.update { it.copy(batch = batch) }
            }
        }
    }

    fun enqueue(uriStrings: List<String>, preferredCharsetName: String? = null) {
        if (uriStrings.isEmpty()) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                scheduler.enqueue(uriStrings.distinct(), preferredCharsetName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.update { it.copy(errorMessage = "无法创建导入任务") }
            } finally {
                mutableUiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun prepareImport(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableUiState.update {
                it.copy(
                    isSubmitting = true,
                    encodingDecision = null,
                    preflightFailures = emptyList(),
                    errorMessage = null,
                )
            }
            try {
                preflightItems = preflightService.analyze(uriStrings.distinct())
                selectedCharsets.clear()
                preflightItems.filter { item -> item.isValid && item.encoding?.isCertain == true }
                    .forEach { item -> selectedCharsets[item.uriString] = item.encoding!!.recommendedCharsetName }
                val failures = preflightItems.filterNot(ImportPreflightItem::isValid)
                val firstDecision = nextEncodingDecision()
                mutableUiState.update {
                    it.copy(
                        isSubmitting = false,
                        encodingDecision = firstDecision,
                        preflightFailures = failures,
                    )
                }
                if (firstDecision == null) enqueuePreparedItems()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.update { it.copy(isSubmitting = false, errorMessage = "无法检查所选 TXT") }
            }
        }
    }

    fun confirmEncoding(item: ImportPreflightItem, charsetName: String) {
        if (item.uriString != mutableUiState.value.encodingDecision?.uriString) return
        selectedCharsets[item.uriString] = charsetName
        val next = nextEncodingDecision()
        mutableUiState.update { it.copy(encodingDecision = next) }
        if (next == null) {
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { enqueuePreparedItems() }
        }
    }

    fun cancelEncodingReview() {
        preflightItems = emptyList()
        selectedCharsets.clear()
        mutableUiState.update { it.copy(encodingDecision = null, isSubmitting = false) }
    }

    fun resolveDuplicate(item: ImportItemState, resolution: DuplicateResolution) {
        launchItemAction { scheduler.resolveDuplicate(item, resolution) }
    }

    fun retry(item: ImportItemState) {
        launchItemAction { scheduler.retry(item) }
    }

    fun editImportedBook(item: ImportItemState) {
        val bookId = item.bookId ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val book = bookManager.get(bookId) ?: error("找不到刚导入的书籍")
                mutableUiState.update { it.copy(editingBook = book, errorMessage = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.update { it.copy(errorMessage = "无法读取书籍信息") }
            }
        }
    }

    fun saveImportedBookMetadata(title: String, author: String?) {
        val book = mutableUiState.value.editingBook ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutableUiState.update { it.copy(isSavingMetadata = true, errorMessage = null) }
            try {
                bookManager.updateMetadata(book.id, title, author)
                mutableUiState.update { it.copy(editingBook = null) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.update { it.copy(errorMessage = "无法保存书籍信息") }
            } finally {
                mutableUiState.update { it.copy(isSavingMetadata = false) }
            }
        }
    }

    fun dismissBookMetadataEditor() {
        mutableUiState.update { it.copy(editingBook = null) }
    }

    fun dismissError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    private fun launchItemAction(action: suspend () -> Unit) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableUiState.update { it.copy(errorMessage = "操作失败") }
            }
        }
    }

    private fun nextEncodingDecision(): ImportPreflightItem? = preflightItems.firstOrNull { item ->
        item.isValid && item.encoding?.isCertain == false && item.uriString !in selectedCharsets
    }

    private suspend fun enqueuePreparedItems() {
        val requests = preflightItems.mapNotNull { item ->
            val charset = selectedCharsets[item.uriString] ?: return@mapNotNull null
            ImportRequest(item.uriString, charset)
        }
        if (requests.isEmpty()) return
        mutableUiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        try {
            scheduler.enqueueRequests(requests)
            preflightItems = emptyList()
            selectedCharsets.clear()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            mutableUiState.update { it.copy(errorMessage = "无法创建导入任务") }
        } finally {
            mutableUiState.update { it.copy(isSubmitting = false) }
        }
    }
}

package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.xinyue.reader.core.database.dao.ImportedFontDao
import com.xinyue.reader.core.database.entity.ImportedFontEntity
import com.xinyue.reader.core.domain.model.FontRemovalResult
import com.xinyue.reader.core.domain.model.ImportSource
import com.xinyue.reader.core.domain.time.EpochClock
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class LocalImportedFontRepositoryTest {
    private lateinit var root: File
    private lateinit var dao: FakeImportedFontDao
    private lateinit var validator: FakeFontValidator

    @Before
    fun setUp() {
        root = Files.createTempDirectory("xinyue-font-test").toFile()
        dao = FakeImportedFontDao()
        validator = FakeFontValidator()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `imports ttf otf and ttc through staging then publishes private files`() = runTest {
        val repository = repository()
        for ((index, extension) in listOf("ttf", "OTF", "TtC").withIndex()) {
            val bytes = "font-$extension".encodeToByteArray()
            val font = repository.importFont(source("字体.$extension", bytes))
            val stored = File(root, "fonts/${font.id}/font.bin")

            assertThat(stored.readBytes()).isEqualTo(bytes)
            assertThat(font.displayName).isEqualTo("字体.$extension")
            assertThat(dao.get(font.id)?.privateRelativePath).isEqualTo("fonts/${font.id}/font.bin")
            assertThat(font.id).isEqualTo("font-${index + 1}")
        }
        assertThat(File(root, "font-staging").walkTopDown().filter(File::isFile).toList()).isEmpty()
    }

    @Test
    fun `unknown declared size is bounded by actual 64 MiB streaming reads`() = runTest {
        val repository = repository()
        val source = ImportSource("huge.ttf", ImportSource.UNKNOWN_SIZE_BYTES) {
            RepeatingInputStream(LocalImportedFontRepository.MAX_FONT_BYTES + 1)
        }

        assertFailsWith<IllegalArgumentException> { repository.importFont(source) }
        assertThat(dao.values.value).isEmpty()
        assertThat(root.walkTopDown().filter(File::isFile).toList()).isEmpty()
    }

    @Test
    fun `rejects empty unsupported and declared oversized sources before publishing`() = runTest {
        val repository = repository()

        assertFailsWith<IllegalArgumentException> { repository.importFont(source("empty.ttf", byteArrayOf())) }
        assertFailsWith<IllegalArgumentException> { repository.importFont(source("font.zip", byteArrayOf(1))) }
        assertFailsWith<IllegalArgumentException> {
            repository.importFont(
                ImportSource("font.otf", LocalImportedFontRepository.MAX_FONT_BYTES + 1) {
                    error("oversized declared source must not be opened")
                },
            )
        }
        assertThat(dao.values.value).isEmpty()
    }

    @Test
    fun `deduplicates by sha and remains independent after source deletion`() = runTest {
        val bytes = "same-font-payload".encodeToByteArray()
        val sourceFile = File(root, "outside.ttf").apply { writeBytes(bytes) }
        val repository = repository()

        val first = repository.importFont(
            ImportSource(sourceFile.name, sourceFile.length()) { sourceFile.inputStream() },
        )
        sourceFile.delete()
        val second = repository.importFont(source("copy.ttf", bytes))

        assertThat(second).isEqualTo(first)
        assertThat(dao.values.value).hasSize(1)
        assertThat(File(root, "fonts/${first.id}/font.bin").readBytes()).isEqualTo(bytes)
    }

    @Test
    fun `validator failure removes staging and leaves no metadata`() = runTest {
        validator.failure = IllegalArgumentException("bad font")
        val repository = repository()

        assertFailsWith<IllegalArgumentException> {
            repository.importFont(source("bad.ttf", "bad".encodeToByteArray()))
        }

        assertThat(dao.values.value).isEmpty()
        assertThat(root.walkTopDown().filter(File::isFile).toList()).isEmpty()
    }

    @Test
    fun `removal is blocked while referenced and removes unreferenced private font`() = runTest {
        val counter = FakeFontReferenceCounter(referenceCount = 2)
        val repository = repository(counter)
        val font = repository.importFont(source("used.ttf", "used".encodeToByteArray()))

        assertThat(repository.remove(font.id)).isEqualTo(FontRemovalResult.InUse(2))
        assertThat(File(root, "fonts/${font.id}/font.bin").exists()).isTrue()

        counter.referenceCount = 0
        assertThat(repository.remove(font.id)).isEqualTo(FontRemovalResult.Removed)
        assertThat(repository.remove(font.id)).isEqualTo(FontRemovalResult.NotFound)
        assertThat(File(root, "fonts/${font.id}").exists()).isFalse()
    }

    @Test
    fun `file store opens only dao recorded readable files beneath private font root`() = runTest {
        val valid = File(root, "fonts/safe/font.bin").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val escaped = File(requireNotNull(root.parentFile), "escaped-font.bin").apply { writeBytes(byteArrayOf(9)) }
        val warnings = mutableListOf<String>()
        val store = ImportedFontFileStore(root, dao, backgroundScope, warnings::add)
        dao.values.value = listOf(
            entity("safe", "fonts/safe/font.bin"),
            entity("escape", "../${escaped.name}"),
            entity("missing", "fonts/missing/font.bin"),
        )
        runCurrent()

        assertThat(store.open("safe")).isEqualTo(valid)
        assertThat(store.open("escape")).isNull()
        assertThat(store.open("missing")).isNull()
        assertThat(store.open("missing")).isNull()
        assertThat(store.open("unknown")).isNull()
        assertThat(warnings).containsExactly("escape", "missing")
        escaped.delete()
    }

    private fun repository(counter: FakeFontReferenceCounter = FakeFontReferenceCounter()) =
        LocalImportedFontRepository(
            rootDirectory = root,
            dao = dao,
            validator = validator,
            referenceCounter = counter,
            idFactory = { "font-${dao.values.value.size + 1}" },
            clock = EpochClock { 123 },
        )

    private fun source(name: String, bytes: ByteArray) = ImportSource(name, bytes.size.toLong()) {
        ByteArrayInputStream(bytes)
    }

    private fun entity(id: String, path: String) = ImportedFontEntity(
        id = id,
        displayName = id,
        privateRelativePath = path,
        contentSha256 = "sha-$id",
        sizeBytes = 1,
        createdAtEpochMillis = 1,
    )
}

private class FakeFontValidator : FontValidator {
    var failure: RuntimeException? = null
    override suspend fun validate(file: File) {
        failure?.let { throw it }
    }
}

private class FakeFontReferenceCounter(var referenceCount: Int = 0) : FontReferenceCounter {
    override suspend fun count(fontId: String): Int = referenceCount
}

private class FakeImportedFontDao : ImportedFontDao {
    val values = MutableStateFlow<List<ImportedFontEntity>>(emptyList())
    override fun observeAll(): Flow<List<ImportedFontEntity>> = values
    override suspend fun getAllForBackup(): List<ImportedFontEntity> = values.value
    override suspend fun get(id: String): ImportedFontEntity? = values.value.firstOrNull { it.id == id }
    override suspend fun findBySha256(sha256: String): ImportedFontEntity? =
        values.value.firstOrNull { it.contentSha256 == sha256 }
    override suspend fun insert(entity: ImportedFontEntity) {
        check(values.value.none { it.id == entity.id || it.contentSha256 == entity.contentSha256 })
        values.value = values.value + entity
    }
    override suspend fun delete(id: String) {
        values.value = values.value.filterNot { it.id == id }
    }
}

private class RepeatingInputStream(private var remaining: Long) : InputStream() {
    override fun read(): Int = if (remaining-- > 0) 0x41 else -1
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (remaining <= 0) return -1
        val count = minOf(length.toLong(), remaining).toInt()
        buffer.fill(0x41, offset, offset + count)
        remaining -= count
        return count
    }
}

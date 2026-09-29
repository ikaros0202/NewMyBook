package com.xinyue.reader.core.data

import com.xinyue.reader.core.domain.model.BackupEntryKind
import com.xinyue.reader.core.domain.model.BackupOptions
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.CRC32

data class BackupAsset(
    val archivePath: String,
    val kind: BackupEntryKind,
    val sourceFile: File,
    val expectedSize: Long,
)

data class HashedBackupAsset(val asset: BackupAsset, val sha256: String, val crc32: Long)

data class BackupAssetInventoryResult(
    val catalog: BackupCatalogSnapshot,
    val assets: List<BackupAsset>,
)

class BackupAssetInventory internal constructor(
    privateRoot: File,
    private val isSymbolicLink: (File) -> Boolean = { Files.isSymbolicLink(it.toPath()) },
    private val openInput: (File) -> InputStream = File::inputStream,
) {
    private val root = privateRoot.absoluteFile.toPath().normalize()

    fun collect(snapshot: BackupCatalogSnapshot, options: BackupOptions): BackupAssetInventoryResult {
        val assets = ArrayList<BackupAsset>()
        val physicalFiles = HashSet<String>()
        val booksById = snapshot.books.associateBy { it.id }
        snapshot.bookSources.sortedBy { it.bookId }.forEach { source ->
            val book = requireNotNull(booksById[source.bookId]) { "备份书籍文件没有对应记录" }
            if (options.includeBookText) {
                assets += verifiedAsset(book.originalAssetPath, BackupEntryKind.ORIGINAL_TEXT, source.originalRelativePath, physicalFiles)
                assets += verifiedAsset(book.normalizedAssetPath, BackupEntryKind.NORMALIZED_TEXT, source.normalizedRelativePath, physicalFiles)
                assets += verifiedAsset(book.offsetIndexAssetPath, BackupEntryKind.OFFSET_INDEX, source.offsetIndexRelativePath, physicalFiles)
            }
            if (source.customCoverRelativePath != null) {
                assets += verifiedAsset(
                    book.customCoverAssetPath,
                    BackupEntryKind.CUSTOM_COVER,
                    source.customCoverRelativePath,
                    physicalFiles,
                )
            }
        }
        val fontsById = snapshot.fonts.associateBy { it.id }
        if (options.includeFonts) {
            snapshot.fontSources.sortedBy { it.fontId }.forEach { source ->
                val font = requireNotNull(fontsById[source.fontId]) { "备份字体文件没有对应记录" }
                assets += verifiedAsset(font.assetPath, BackupEntryKind.FONT, source.relativePath, physicalFiles)
            }
        }
        val adjustedBooks = if (options.includeBookText) {
            snapshot.books
        } else {
            snapshot.books.map {
                it.copy(originalAssetPath = null, normalizedAssetPath = null, offsetIndexAssetPath = null)
            }
        }
        val adjustedCatalog = snapshot.copy(
            books = adjustedBooks,
            fonts = if (options.includeFonts) snapshot.fonts else emptyList(),
            fontSources = if (options.includeFonts) snapshot.fontSources else emptyList(),
        )
        return BackupAssetInventoryResult(adjustedCatalog, assets)
    }

    fun hash(asset: BackupAsset, onBytes: (Long) -> Unit = {}): HashedBackupAsset {
        require(asset.sourceFile.length() == asset.expectedSize) { "数据在备份过程中发生变化，请重试" }
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        var observed = 0L
        openInput(asset.sourceFile).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                observed += count
                check(observed <= asset.expectedSize) { "数据在备份过程中发生变化，请重试" }
                digest.update(buffer, 0, count)
                crc.update(buffer, 0, count)
                onBytes(count.toLong())
            }
        }
        check(observed == asset.expectedSize && asset.sourceFile.length() == asset.expectedSize) {
            "数据在备份过程中发生变化，请重试"
        }
        return HashedBackupAsset(asset, digest.digest().joinToString("") { "%02x".format(it) }, crc.value)
    }

    internal fun open(asset: BackupAsset): InputStream = openInput(asset.sourceFile)

    private fun verifiedAsset(
        archivePath: String?,
        kind: BackupEntryKind,
        relativePath: String,
        physicalFiles: MutableSet<String>,
    ): BackupAsset {
        requireNotNull(archivePath) { "备份资产目录引用缺失" }
        require(relativePath.isNotBlank() && '\\' !in relativePath && !relativePath.startsWith('/')) {
            "备份私有文件路径无效"
        }
        val segments = relativePath.split('/')
        require(segments.all { it.isNotEmpty() && it != "." && it != ".." && it.none(Char::isISOControl) }) {
            "备份私有文件路径无效"
        }
        val resolved = segments.fold(root) { path, segment -> path.resolve(segment) }.normalize()
        require(resolved.startsWith(root) && resolved != root) { "备份私有文件路径越界" }
        var current = root
        for (segment in root.relativize(resolved)) {
            current = current.resolve(segment)
            require(!isSymbolicLink(current.toFile())) { "备份私有文件不能是符号链接" }
        }
        val file = resolved.toFile()
        require(file.isFile && file.canRead()) { "备份所需文件缺失或不可读" }
        val physicalPath = file.canonicalPath
        require(physicalFiles.add(physicalPath)) { "备份资产重复引用同一文件" }
        val size = file.length()
        BackupSafetyLimits.requireEntrySize(kind, size)
        return BackupAsset(archivePath, kind, file, size)
    }
}

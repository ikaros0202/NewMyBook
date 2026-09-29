package com.xinyue.reader.core.data

import android.content.Context
import com.xinyue.reader.core.database.entity.BookEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookCoverModelResolver internal constructor(rootDirectory: File) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir)

    private val root = rootDirectory.canonicalFile
    private val booksRoot = File(root, "books").canonicalFile

    fun resolve(book: BookEntity): File? {
        val relativePath = book.customCoverPath ?: return null
        val expectedDirectory = File(booksRoot, book.id).canonicalFile
        val resolved = File(root, relativePath).canonicalFile
        val allowed = setOf(
            File(expectedDirectory, "cover.webp").canonicalFile,
            File(expectedDirectory, "cover.png").canonicalFile,
        )
        return resolved.takeIf {
            it in allowed && it.isFile && it.toPath().startsWith(expectedDirectory.toPath())
        }
    }
}

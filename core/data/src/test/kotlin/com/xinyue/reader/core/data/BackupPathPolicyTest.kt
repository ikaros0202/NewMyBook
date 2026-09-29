package com.xinyue.reader.core.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupPathPolicyTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `approved forward slash path resolves under extraction root`() {
        val root = temporaryFolder.newFolder("extract")

        val normalized = BackupPathPolicy.normalize("assets/books/book-1/content.txt")
        val resolved = BackupPathPolicy.resolve(root, normalized)

        assertThat(normalized).isEqualTo("assets/books/book-1/content.txt")
        assertThat(resolved.toPath().normalize().startsWith(root.toPath().normalize())).isTrue()
    }

    @Test
    fun `platform ambiguous traversal and malformed paths are rejected`() {
        val invalid = listOf(
            "../escape", "a/../escape", "/absolute", "C:/drive", "C:\\drive", "a\\b",
            "./dot", "a/./dot", "a//empty", "trailing/", "", "nul\u0000name", "line\nname",
            "a".repeat(513),
        )

        invalid.forEach { path ->
            val error = runCatching { BackupPathPolicy.normalize(path) }.exceptionOrNull()
            assertWithMessage(path.replace("\u0000", "NUL")).that(error).isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `unicode-normalized duplicate names are rejected`() {
        val composed = "catalog/caf\u00e9.json"
        val decomposed = "catalog/cafe\u0301.json"

        val error = runCatching { BackupPathPolicy.requireUnique(listOf(composed, decomposed)) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `resolve refuses an injected escape even after validation boundary`() {
        val root = temporaryFolder.newFolder("resolve")
        val outside = File(root, "../outside").normalize()

        val error = runCatching { BackupPathPolicy.resolve(root, outside.path) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }
}

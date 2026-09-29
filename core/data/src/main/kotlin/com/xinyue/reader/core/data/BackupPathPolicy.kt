package com.xinyue.reader.core.data

import java.io.File
import java.text.Normalizer

object BackupPathPolicy {
    fun normalize(path: String): String {
        require(path.isNotEmpty() && path.length <= 512) { "备份条目路径无效" }
        require(!path.startsWith('/') && !DRIVE_PREFIX.containsMatchIn(path)) { "备份条目路径无效" }
        require('\\' !in path && ':' !in path && path.none { it.code == 0 || it.isISOControl() }) {
            "备份条目路径无效"
        }
        val segments = path.split('/')
        require(segments.all { it.isNotEmpty() && it != "." && it != ".." }) { "备份条目路径无效" }
        return Normalizer.normalize(path, Normalizer.Form.NFC)
    }

    fun requireUnique(paths: Iterable<String>): List<String> {
        val unique = HashSet<String>()
        return paths.map(::normalize).also { normalized ->
            require(normalized.all(unique::add)) { "备份条目路径重复" }
        }
    }

    fun resolve(root: File, path: String): File {
        val normalizedPath = normalize(path)
        val normalizedRoot = root.absoluteFile.toPath().normalize()
        val resolved = normalizedPath.split('/').fold(normalizedRoot) { current, segment -> current.resolve(segment) }.normalize()
        require(resolved != normalizedRoot && resolved.startsWith(normalizedRoot)) { "备份条目路径越界" }
        return resolved.toFile()
    }

    private val DRIVE_PREFIX = Regex("^[A-Za-z]:")
}

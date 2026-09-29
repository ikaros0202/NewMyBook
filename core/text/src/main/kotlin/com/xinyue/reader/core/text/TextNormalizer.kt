package com.xinyue.reader.core.text

object TextNormalizer {
    fun normalize(text: String): String =
        text
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
}

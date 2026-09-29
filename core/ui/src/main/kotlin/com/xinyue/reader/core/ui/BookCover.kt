package com.xinyue.reader.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.xinyue.reader.core.domain.model.Book
import java.io.File

@Composable
fun BookCover(book: Book, modifier: Modifier = Modifier) {
    val model = bookCoverModel(book)
    Box(modifier = modifier) {
        GeneratedBookCover(book.title, Modifier.fillMaxSize())
        if (model != null) {
            var loaded by remember(model) { mutableStateOf(false) }
            AsyncImage(
                model = model,
                contentDescription = "《${book.title}》的自定义封面",
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (loaded) 1f else 0f },
            )
        }
    }
}

internal fun bookCoverModel(book: Book): File? =
    book.customCoverModel.takeIf { book.customCoverPath != null }

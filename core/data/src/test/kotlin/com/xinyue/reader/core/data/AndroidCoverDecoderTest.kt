package com.xinyue.reader.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class AndroidCoverDecoderTest {
    @Test
    fun `invalid bounds are rejected and sampling is bounded without EXIF metadata`() {
        assertFailsWith<IllegalArgumentException> { validateCoverBounds(0, 100) }
        assertFailsWith<IllegalArgumentException> { validateCoverBounds(8_000, 6_000) }
        assertThat(calculateCoverSampleSize(3_200, 2_400)).isEqualTo(2)
        assertThat(calculateCoverSampleSize(1_600, 2_400)).isEqualTo(1)
    }

    @Test
    fun `corrupt bytes fail and a valid bitmap is losslessly encoded`() {
        val root = Files.createTempDirectory("cover-decoder").toFile()
        val corrupt = File(root, "corrupt.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val output = File(root, "output.tmp")
        val decoder = AndroidCoverDecoder()
        assertFailsWith<IllegalArgumentException> { decoder.decode(corrupt, output) }

        val input = File(root, "valid.png")
        val bitmap = Bitmap.createBitmap(32, 48, Bitmap.Config.ARGB_8888)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        assertThat(decoder.decode(input, output)).isEqualTo(CoverEncoding.WEBP)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.path, bounds)
        assertThat(bounds.outWidth).isEqualTo(32)
        assertThat(bounds.outHeight).isEqualTo(48)
        root.deleteRecursively()
    }
}

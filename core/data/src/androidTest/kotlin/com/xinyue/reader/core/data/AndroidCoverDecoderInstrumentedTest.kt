package com.xinyue.reader.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.CRC32
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidCoverDecoderInstrumentedTest {
    private val root = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "cover-decoder-test")

    @Test
    fun validJpegPngAndWebpDecodeToBoundedPrivateImage() {
        root.deleteRecursively()
        root.mkdirs()
        val bitmap = Bitmap.createBitmap(96, 144, Bitmap.Config.ARGB_8888)
        val inputs = listOf(
            "fixture.jpg" to Bitmap.CompressFormat.JPEG,
            "fixture.png" to Bitmap.CompressFormat.PNG,
            "fixture.webp" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSLESS
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            },
        ).map { (name, format) ->
            File(root, name).also { file ->
                file.outputStream().use { assertThat(bitmap.compress(format, 100, it)).isTrue() }
            }
        }
        bitmap.recycle()

        inputs.forEachIndexed { index, input ->
            val output = File(root, "output-$index.tmp")
            val expectedEncoding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                CoverEncoding.WEBP
            } else {
                CoverEncoding.PNG
            }
            assertThat(AndroidCoverDecoder().decode(input, output)).isEqualTo(expectedEncoding)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.path, bounds)
            assertThat(bounds.outWidth).isEqualTo(96)
            assertThat(bounds.outHeight).isEqualTo(144)
        }
        root.deleteRecursively()
    }

    @Test
    fun corruptAndOversizedMetadataAreRejectedBeforeBitmapAllocation() {
        root.deleteRecursively()
        root.mkdirs()
        val output = File(root, "output.tmp")
        val corrupt = File(root, "corrupt.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertFailsWith<IllegalArgumentException> { AndroidCoverDecoder().decode(corrupt, output) }

        val small = File(root, "small.png")
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        small.outputStream().use { assertThat(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)).isTrue() }
        bitmap.recycle()
        val bytes = small.readBytes()
        writeInt(bytes, 16, 8_000)
        writeInt(bytes, 20, 6_000)
        val crc = CRC32().apply { update(bytes, 12, 17) }.value.toInt()
        writeInt(bytes, 29, crc)
        val oversized = File(root, "oversized.png").apply { writeBytes(bytes) }

        assertFailsWith<IllegalArgumentException> { AndroidCoverDecoder().decode(oversized, output) }
        root.deleteRecursively()
    }

    private fun writeInt(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 24).toByte()
        bytes[offset + 1] = (value ushr 16).toByte()
        bytes[offset + 2] = (value ushr 8).toByte()
        bytes[offset + 3] = value.toByte()
    }
}

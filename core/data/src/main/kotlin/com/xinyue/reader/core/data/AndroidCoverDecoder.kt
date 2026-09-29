package com.xinyue.reader.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.annotation.TargetApi
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class CoverEncoding(val extension: String) {
    WEBP("webp"),
    PNG("png"),
}

fun interface CoverDecoder {
    fun decode(source: File, output: File): CoverEncoding
}

class AndroidCoverDecoder @Inject constructor() : CoverDecoder {
    override fun decode(source: File, output: File): CoverEncoding {
        requireSupportedImageHeader(source)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        validateCoverBounds(bounds.outWidth, bounds.outHeight)

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateCoverSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = requireNotNull(BitmapFactory.decodeFile(source.path, options)) {
            "无法解码所选封面"
        }
        val encoding = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CoverEncoding.WEBP
        } else {
            CoverEncoding.PNG
        }
        val format = if (encoding == CoverEncoding.WEBP) {
            losslessWebpFormat()
        } else {
            Bitmap.CompressFormat.PNG
        }
        try {
            FileOutputStream(output).use { stream ->
                check(decoded.compress(format, 100, stream)) { "无法编码私有封面" }
                stream.fd.sync()
            }
        } catch (failure: Throwable) {
            output.delete()
            throw failure
        } finally {
            decoded.recycle()
        }
        return encoding
    }
}

@TargetApi(Build.VERSION_CODES.R)
private fun losslessWebpFormat(): Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP_LOSSLESS

internal fun requireSupportedImageHeader(source: File) {
    val header = ByteArray(12)
    val count = source.inputStream().use { it.read(header) }
    val isPng = count >= 8 && header.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)
    val isJpeg = count >= 2 &&
        header[0].toInt() and 0xff == 0xff &&
        header[1].toInt() and 0xff == 0xd8
    val isWebp = count >= 12 &&
        header.copyOfRange(0, 4).contentEquals(RIFF_SIGNATURE) &&
        header.copyOfRange(8, 12).contentEquals(WEBP_SIGNATURE)
    require(isPng || isJpeg || isWebp) { "不支持或已损坏的封面图片" }
}

internal fun validateCoverBounds(width: Int, height: Int) {
    require(width > 0 && height > 0) { "无法读取封面尺寸" }
    require(width.toLong() * height.toLong() <= MAX_COVER_PIXELS) { "封面像素尺寸过大" }
}

internal fun calculateCoverSampleSize(width: Int, height: Int): Int {
    validateCoverBounds(width, height)
    var sampleSize = 1
    while (
        ceilDiv(width, sampleSize) > MAX_COVER_WIDTH ||
        ceilDiv(height, sampleSize) > MAX_COVER_HEIGHT
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor

private const val MAX_COVER_PIXELS = 40_000_000L
private const val MAX_COVER_WIDTH = 1_600
private const val MAX_COVER_HEIGHT = 2_400
private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
)
private val RIFF_SIGNATURE = "RIFF".encodeToByteArray()
private val WEBP_SIGNATURE = "WEBP".encodeToByteArray()

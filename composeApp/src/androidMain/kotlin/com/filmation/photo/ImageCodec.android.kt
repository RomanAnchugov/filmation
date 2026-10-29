package com.filmation.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.exifinterface.media.ExifInterface
import com.filmation.fx.PixelImage
import java.io.ByteArrayInputStream

actual fun decodeToPixelImage(bytes: ByteArray, maxSide: Int): PixelImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Грубое прореживание ещё на этапе декода (кратно 2) для экономии памяти.
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null

    // EXIF-поворот (снимки с камеры часто лежат повёрнутыми).
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation != 0f) {
        val m = Matrix().apply { postRotate(rotation) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    // Точная подгонка под maxSide.
    val maxDim = maxOf(bmp.width, bmp.height)
    if (maxDim > maxSide) {
        val scale = maxSide.toFloat() / maxDim
        bmp = Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * scale).toInt().coerceAtLeast(1),
            (bmp.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    val pixels = IntArray(bmp.width * bmp.height)
    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    val w = bmp.width
    val h = bmp.height
    bmp.recycle()
    return PixelImage(w, h, pixels)
}

actual fun encodeJpeg(image: PixelImage, quality: Int): ByteArray? {
    return runCatching {
        val bmp = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        bmp.recycle()
        out.toByteArray()
    }.getOrNull()
}

actual fun toImageBitmap(image: PixelImage): ImageBitmap =
    Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888).asImageBitmap()

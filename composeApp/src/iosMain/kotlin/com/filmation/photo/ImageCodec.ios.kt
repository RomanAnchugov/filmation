package com.filmation.photo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.filmation.fx.PixelImage
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface

actual fun decodeToPixelImage(bytes: ByteArray, maxSide: Int): PixelImage? {
    val img = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    try {
        return if (maxOf(img.width, img.height) > maxSide) {
            val scaled = skiaScale(img, maxSide)
            try {
                readPixelsOf(scaled)
            } finally {
                scaled.close()
            }
        } else {
            readPixelsOf(img)
        }
    } finally {
        img.close()
    }
}

actual fun encodeJpeg(image: PixelImage, quality: Int): ByteArray? {
    val bmp = skiaBitmapFrom(image)
    val img = Image.makeFromBitmap(bmp)
    try {
        return img.encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
    } finally {
        img.close()
        bmp.close()
    }
}

actual fun toImageBitmap(image: PixelImage): ImageBitmap {
    val bmp = skiaBitmapFrom(image)
    val img = Image.makeFromBitmap(bmp)
    try {
        return img.toComposeImageBitmap()
    } finally {
        img.close()
        bmp.close()
    }
}

/** Пиксели skia Image -> PixelImage (через Bitmap, память освобождается). */
private fun readPixelsOf(image: Image): PixelImage? {
    val b = Bitmap.makeFromImage(image)
    try {
        val info = ImageInfo.makeN32Premul(image.width, image.height)
        val raw = b.readPixels(info, info.minRowBytes, 0, 0) ?: return null
        return PixelImage(image.width, image.height, raw.toIntArrayLE())
    } finally {
        b.close()
    }
}

/** Уменьшить skia Image до maxSide по длинной стороне (всегда новый Image). */
private fun skiaScale(src: Image, maxSide: Int): Image {
    val scale = maxSide.toFloat() / maxOf(src.width, src.height)
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(w, h)
    val paint = Paint()
    try {
        surface.canvas.drawImageRect(
            src,
            Rect.makeXYWH(0f, 0f, src.width.toFloat(), src.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            paint,
        )
        return surface.makeImageSnapshot()
    } finally {
        surface.close()
        paint.close()
    }
}

private fun skiaBitmapFrom(image: PixelImage): Bitmap {
    val info = ImageInfo.makeN32Premul(image.width, image.height)
    val bmp = Bitmap()
    bmp.allocPixels(info)
    bmp.installPixels(image.pixels.toIntArrayLE())
    return bmp
}

/** ARGB-инты <-> LE-байты (память Skia N32 на little-endian: BGRA-байты = ARGB-инт). */
private fun IntArray.toIntArrayLE(): ByteArray {
    val out = ByteArray(size * 4)
    var i = 0
    for (v in this) {
        out[i++] = (v and 0xFF).toByte()
        out[i++] = ((v ushr 8) and 0xFF).toByte()
        out[i++] = ((v ushr 16) and 0xFF).toByte()
        out[i++] = ((v ushr 24) and 0xFF).toByte()
    }
    return out
}

private fun ByteArray.toIntArrayLE(): IntArray {
    val n = size / 4
    val out = IntArray(n)
    for (j in 0 until n) {
        val b0 = this[j * 4].toInt() and 0xFF
        val b1 = this[j * 4 + 1].toInt() and 0xFF
        val b2 = this[j * 4 + 2].toInt() and 0xFF
        val b3 = this[j * 4 + 3].toInt() and 0xFF
        out[j] = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
    }
    return out
}

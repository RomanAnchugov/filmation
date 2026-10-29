package com.filmation.photo

import androidx.compose.ui.graphics.ImageBitmap
import com.filmation.fx.PixelImage

/**
 * Платформенное кодирование/декодирование изображений.
 *
 * Android: BitmapFactory/Bitmap (EXIF-поворот применяется при декодировании).
 * iOS: skiko (Skia) — декодирует JPEG/PNG/WebP, кодирует JPEG.
 */

/** Декодировать байты в [PixelImage] (ARGB), при необходимости повернуть по EXIF
 *  и ужать длинную сторону до [maxSide]. */
expect fun decodeToPixelImage(bytes: ByteArray, maxSide: Int): PixelImage?

/** Закодировать [PixelImage] в JPEG заданного качества. */
expect fun encodeJpeg(image: PixelImage, quality: Int): ByteArray?

/** [PixelImage] -> [ImageBitmap] для отображения в Compose. */
expect fun toImageBitmap(image: PixelImage): ImageBitmap

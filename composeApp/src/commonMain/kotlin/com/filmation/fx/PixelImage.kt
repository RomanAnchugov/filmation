package com.filmation.fx

/**
 * Изображение в общем формате: плотный IntArray пикселей ARGB (0xAARRGGBB), альфа всегда 255.
 * Единое представление для движка эффектов на обеих платформах
 * (Android: Bitmap.getPixels, iOS: skiko Bitmap.readPixels).
 */
class PixelImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) { "pixels.size ${pixels.size} != $width*$height" }
    }
}

/**
 * Уменьшить изображение до [maxSide] по длинной стороне, усредняя блоки (box).
 * Даёт мягкую низкочастотную копию — идеальна как источник для bloom/халации.
 */
fun PixelImage.scaledTo(maxSide: Int): PixelImage {
    val maxDim = maxOf(width, height)
    if (maxDim <= maxSide) return this
    val scale = maxSide.toFloat() / maxDim
    val w = (width * scale).toInt().coerceAtLeast(1)
    val h = (height * scale).toInt().coerceAtLeast(1)
    val out = IntArray(w * h)
    val fx = width.toFloat() / w
    val fy = height.toFloat() / h

    for (y in 0 until h) {
        val y0 = (y * fy).toInt()
        val y1 = (((y + 1) * fy).toInt()).coerceAtMost(height)
        for (x in 0 until w) {
            val x0 = (x * fx).toInt()
            val x1 = (((x + 1) * fx).toInt()).coerceAtMost(width)
            var r = 0; var g = 0; var b = 0; var n = 0
            for (sy in y0 until y1) {
                for (sx in x0 until x1) {
                    val c = pixels[sy * width + sx]
                    r += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
            }
            out[y * w + x] = 0xFF shl 24 or
                ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
    }
    return PixelImage(w, h, out)
}

/**
 * Размытие box (сепарабельное, два прохода; скользящее окно — O(1) на пиксель,
 * радиус не влияет на время). Применяется к маленькой «glow»-копии —
 * конвейер bloom/халации.
 */
fun PixelImage.boxBlurred(radiusPx: Float): PixelImage {
    if (radiusPx < 1f) return this
    val r = radiusPx.toInt().coerceIn(1, 48)
    val tmp = IntArray(pixels.size)
    val out = IntArray(pixels.size)

    // Горизонтальный проход pixels -> tmp
    for (y in 0 until height) {
        val row = y * width
        var accR = 0; var accG = 0; var accB = 0
        // инициализация окна [0..r]
        for (i in 0..r.coerceAtMost(width - 1)) {
            val c = pixels[row + i]
            accR += (c shr 16) and 0xFF; accG += (c shr 8) and 0xFF; accB += c and 0xFF
        }
        var n = r.coerceAtMost(width - 1) + 1
        for (x in 0 until width) {
            tmp[row + x] = 0xFF shl 24 or ((accR / n) shl 16) or ((accG / n) shl 8) or (accB / n)
            val addX = x + r + 1
            if (addX < width) {
                val c = pixels[row + addX]
                accR += (c shr 16) and 0xFF; accG += (c shr 8) and 0xFF; accB += c and 0xFF; n++
            }
            val remX = x - r
            if (remX >= 0) {
                val c = pixels[row + remX]
                accR -= (c shr 16) and 0xFF; accG -= (c shr 8) and 0xFF; accB -= c and 0xFF; n--
            }
        }
    }

    // Вертикальный проход tmp -> out
    for (x in 0 until width) {
        var accR = 0; var accG = 0; var accB = 0
        for (i in 0..r.coerceAtMost(height - 1)) {
            val c = tmp[i * width + x]
            accR += (c shr 16) and 0xFF; accG += (c shr 8) and 0xFF; accB += c and 0xFF
        }
        var n = r.coerceAtMost(height - 1) + 1
        for (y in 0 until height) {
            out[y * width + x] = 0xFF shl 24 or ((accR / n) shl 16) or ((accG / n) shl 8) or (accB / n)
            val addY = y + r + 1
            if (addY < height) {
                val c = tmp[addY * width + x]
                accR += (c shr 16) and 0xFF; accG += (c shr 8) and 0xFF; accB += c and 0xFF; n++
            }
            val remY = y - r
            if (remY >= 0) {
                val c = tmp[remY * width + x]
                accR -= (c shr 16) and 0xFF; accG -= (c shr 8) and 0xFF; accB -= c and 0xFF; n--
            }
        }
    }
    return PixelImage(width, height, out)
}

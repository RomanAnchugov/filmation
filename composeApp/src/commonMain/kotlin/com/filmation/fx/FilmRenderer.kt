package com.filmation.fx

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * CPU-рендерер эмуляции плёнки — чистый Kotlin, один код для Android и iOS.
 *
 * Конвейер (порядок эффектов):
 *  1. хроматическая аберрация (радиальный сдвиг каналов к краям)
 *  2. экспозиция (2^EV)
 *  3. bloom (аддитивное свечение яркого)
 *  4. халация (красно-оранжевое свечение яркого, screen blend)
 *  5. плёночная S-кривая контраста
 *  6. fade (матовые тени)
 *  7. ч/б
 *  8. сплит-тонирование (света/тени)
 *  9. виньетка
 * 10. зерно (блочный шум, максимум в полутонах)
 *
 * bloom/халация берутся из маленькой размытой копии [glow] (низкие частоты):
 * размытие выполняется один раз на рендер, в основном цикле — одна билинейная выборка.
 *
 * [isCancelled] опрашивается раз в строку — позволяет прервать устаревший рендер
 * (когда пользователь продолжает двигать слайдер), чтобы не тратить CPU.
 */
class FilmRenderer {

    fun render(
        src: PixelImage,
        glow: PixelImage,
        p: FilmParams,
        outW: Int,
        outH: Int,
        salt: Int = 0,
        isCancelled: () -> Boolean = { false },
    ): PixelImage? {
        val out = IntArray(outW * outH)

        // Предрасчёт размытых копий для bloom и халации.
        val glowMin = minOf(glow.width, glow.height).toFloat()
        val bloomSrc = if (p.bloom > 0.001f) glow.boxBlurred(p.bloomRadius * 0.12f * glowMin) else glow
        val halSrc = if (p.halation > 0.001f) glow.boxBlurred(p.halationRadius * 0.20f * glowMin) else glow

        val exposureMul = 2f.pow(p.exposure)
        val useCA = p.ca > 0.001f
        val caK = p.ca * 0.09f * maxOf(src.width, src.height)
        val useBloom = p.bloom > 0.001f
        val useHalation = p.halation > 0.001f
        val useGrain = p.grain > 0.001f
        val grainPx = (minOf(outW, outH) / 560f).coerceAtLeast(0.8f) * p.grainSize
        val seedX = p.seed * 917
        val seedY = p.seed * 473
        val mono = p.mono
        val warm = p.warm
        val tint = p.tint
        val grainAmp = p.grain * 0.32f

        // Предвычисленные множители сплит-тонирования.
        val warmPos = warm.coerceAtLeast(0f)
        val warmNeg = (-warm).coerceAtLeast(0f)
        val tintPos = tint.coerceAtLeast(0f)
        val tintNeg = (-tint).coerceAtLeast(0f)

        // --- Артефакты: засветы и блики (раскладка зависит от варианта + соли фото) ---
        val aspectH = outH.toFloat() / outW
        val artifacts =
            if (p.artifacts > 0.02f) buildArtifacts(p.artifacts, p.artifactVariant, salt, glow, aspectH)
            else null

        var idx = 0
        for (y in 0 until outH) {
            if (y and 7 == 0 && isCancelled()) return null
            val v = (y + 0.5f) / outH
            for (x in 0 until outW) {
                val u = (x + 0.5f) / outW
                val d = u - 0.5f
                val dv = v - 0.5f
                val r2 = d * d + dv * dv

                // --- 1. Хроматическая аберрация ---
                val sx = u * src.width
                val sy = v * src.height
                var cr: Float; var cg: Float; var cb: Float
                if (useCA) {
                    val k = caK * r2
                    val ox = d * k
                    val oy = dv * k
                    val sR = bilinear(src, sx + ox, sy + oy)
                    val sC = bilinear(src, sx, sy)
                    val sB = bilinear(src, sx - ox, sy - oy)
                    cr = ((sR shr 16) and 0xFF) * INV255
                    cg = ((sC shr 8) and 0xFF) * INV255
                    cb = (sB and 0xFF) * INV255
                } else {
                    val s = bilinear(src, sx, sy)
                    cr = ((s shr 16) and 0xFF) * INV255
                    cg = ((s shr 8) and 0xFF) * INV255
                    cb = (s and 0xFF) * INV255
                }

                // --- 2. Экспозиция ---
                if (exposureMul != 1f) {
                    cr *= exposureMul; cg *= exposureMul; cb *= exposureMul
                }

                // --- 3. Bloom ---
                if (useBloom) {
                    val g = bilinear(bloomSrc, u * bloomSrc.width, v * bloomSrc.height)
                    val gr = ((g shr 16) and 0xFF) * INV255
                    val gg = ((g shr 8) and 0xFF) * INV255
                    val gb = (g and 0xFF) * INV255
                    val w = smoothstep(0.38f, 0.9f, LUMA_R * gr + LUMA_G * gg + LUMA_B * gb) * p.bloom * 1.1f
                    cr += gr * w; cg += gg * w; cb += gb * w
                }

                // --- 4. Халация (screen blend с красно-оранжевым) ---
                if (useHalation) {
                    val g = bilinear(halSrc, u * halSrc.width, v * halSrc.height)
                    val gr = ((g shr 16) and 0xFF) * INV255
                    val gg = ((g shr 8) and 0xFF) * INV255
                    val gb = (g and 0xFF) * INV255
                    val l = LUMA_R * gr + LUMA_G * gg + LUMA_B * gb
                    val w = (smoothstep(0.3f, 0.85f, l) * l * p.halation * 1.6f).coerceIn(0f, 1f)
                    cr = 1f - (1f - cr.coerceIn(0f, 1f)) * (1f - w)
                    cg = 1f - (1f - cg.coerceIn(0f, 1f)) * (1f - 0.30f * w)
                    cb = 1f - (1f - cb.coerceIn(0f, 1f)) * (1f - 0.14f * w)
                }

                // --- 4.5 Артефакты: засветы по краям кадра + блики-«призраки» ---
                if (artifacts != null) {
                    val ax = u
                    val ay = v * aspectH
                    val leaks = artifacts.leaks
                    var li = 0
                    while (li < artifacts.leakCount) {
                        val o = li * 7
                        val dx = ax - leaks[o]
                        val dy = ay - leaks[o + 1]
                        val rl = leaks[o + 2]
                        val q = (dx * dx + dy * dy) / (rl * rl)
                        val e = leaks[o + 3] / (1f + 14f * q)
                        if (e > 0.001f) {
                            cr += e * leaks[o + 4]
                            cg += e * leaks[o + 5]
                            cb += e * leaks[o + 6]
                        }
                        li++
                    }
                    val discs = artifacts.discs
                    var di = 0
                    while (di < artifacts.discCount) {
                        val o = di * 7
                        val dx = ax - discs[o]
                        val dy = ay - discs[o + 1]
                        val rr = discs[o + 2]
                        val q = (dx * dx + dy * dy) / (rr * rr)
                        if (q < 1f) {
                            val e = discs[o + 3] * (1f - q) * (1f - q)
                            cr += e * discs[o + 4]
                            cg += e * discs[o + 5]
                            cb += e * discs[o + 6]
                        }
                        di++
                    }
                }

                // --- 5. Плёночная S-кривая ---
                if (p.curve > 0.001f) {
                    val k = p.curve
                    cr += (cr * cr * (3f - 2f * cr) - cr) * k
                    cg += (cg * cg * (3f - 2f * cg) - cg) * k
                    cb += (cb * cb * (3f - 2f * cb) - cb) * k
                }

                // --- 6. Fade (матовые тени) ---
                if (p.fade > 0.001f) {
                    val k = p.fade * 0.55f
                    cr = cr * (1f - k) + 0.07f * k
                    cg = cg * (1f - k) + 0.066f * k
                    cb = cb * (1f - k) + 0.064f * k
                }

                // --- 7. Ч/б ---
                var l = LUMA_R * cr + LUMA_G * cg + LUMA_B * cb
                if (mono) {
                    cr = l; cg = l; cb = l
                }

                // --- 8. Сплит-тонирование ---
                if (warmPos > 0.001f || warmNeg > 0.001f) {
                    val wh = smoothstep(0.4f, 1f, l)
                    if (warmPos > 0f) {
                        val w = warmPos * wh
                        cr *= 1f + 0.16f * w
                        cg *= 1f + 0.01f * w
                        cb *= 1f - 0.16f * w
                    } else {
                        val w = warmNeg * wh
                        cr *= 1f - 0.12f * w
                        cg *= 1f + 0.01f * w
                        cb *= 1f + 0.18f * w
                    }
                }
                if (tintPos > 0.001f || tintNeg > 0.001f) {
                    val bl = 1f - smoothstep(0f, 0.6f, l)
                    if (tintPos > 0f) {
                        val w = tintPos * bl
                        cr *= 1f - 0.14f * w
                        cg *= 1f + 0.03f * w
                        cb *= 1f + 0.12f * w
                    } else {
                        val w = tintNeg * bl
                        cr *= 1f + 0.14f * w
                        cg *= 1f - 0.06f * w
                        cb *= 1f + 0.10f * w
                    }
                }

                // --- 9. Виньетка ---
                if (p.vignette > 0.001f) {
                    val vr = sqrt(r2) * 2f
                    val vig = 1f - smoothstep(0.3f, 1.25f, vr)
                    val f = 1f + (vig - 1f) * p.vignette
                    cr *= f; cg *= f; cb *= f
                }

                // --- 10. Зерно ---
                if (useGrain) {
                    val gx = (x / grainPx).toInt() + seedX
                    val gy = (y / grainPx).toInt() + seedY
                    val n = grainNoise(gx, gy)
                    val lw = 0.55f + 1.7f * l * (1f - l)
                    val add = n * grainAmp * lw
                    cr += add; cg += add; cb += add
                }

                val ri = (cr * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gi = (cg * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bi = (cb * 255f + 0.5f).toInt().coerceIn(0, 255)
                out[idx++] = 0xFF shl 24 or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return PixelImage(outW, outH, out)
    }

    /**
     * План процедурных артефактов. Записи по 7 float: (x, y, radius, intensity, tintR, tintG, tintB),
     * координаты нормализованные (по y — с поправкой на аспект).
     */
    private class ArtifactPlan(
        val leaks: FloatArray,
        val leakCount: Int,
        val discs: FloatArray,
        val discCount: Int,
    )

    /**
     * Построить раскладку засветов и бликов по [variant] (выбор пользователя) и [salt]
     * (случайная соль фото — у каждой фотки свой набор видов). Детерминировано:
     * превью и экспорт совпадают, повторные загрузки одного фото — тоже.
     *
     * Варьируются: количество утечек (1..3), их края/размеры/оттенки (тёплый, красный,
     * молочный, розовый — «просрочка»), количество бликов (1..4), их позиции вдоль оси
     * «самое яркое пятно кадра → центр» и оттенки.
     */
    private fun buildArtifacts(
        strength: Float,
        variant: Int,
        salt: Int,
        glow: PixelImage,
        aspectH: Float,
    ): ArtifactPlan {
        // rv(id): детерминированное 0..1 от (variant, salt)
        fun rv(id: Int): Float = (grainNoise(variant * 3 + id * 57 + 11, salt * 7 + id) + 1f) * 0.5f

        val leakTints = arrayOf(
            floatArrayOf(1.0f, 0.55f, 0.25f),   // тёплый оранжевый
            floatArrayOf(1.0f, 0.30f, 0.12f),   // красный (засвет задней крышки)
            floatArrayOf(0.95f, 0.80f, 0.55f),  // мягкий молочный
            floatArrayOf(1.0f, 0.45f, 0.48f),   // розоватый (просроченная плёнка)
        )

        val leakCount = 1 + (rv(1) * 2.99f).toInt()
        val leaks = FloatArray(3 * 7)
        for (i in 0 until leakCount) {
            val o = i * 7
            val side = (rv(101 + i * 13) * 4f).toInt()
            val t = 0.15f + 0.7f * rv(102 + i * 13)
            when (side) {
                0 -> { leaks[o] = 0.03f; leaks[o + 1] = t * aspectH }
                1 -> { leaks[o] = 0.97f; leaks[o + 1] = t * aspectH }
                2 -> { leaks[o] = t; leaks[o + 1] = 0.03f * aspectH }
                else -> { leaks[o] = t; leaks[o + 1] = 0.97f * aspectH }
            }
            leaks[o + 2] = 0.45f + 0.5f * rv(103 + i * 13)
            val base = if (i == 0) 0.55f else 0.26f
            leaks[o + 3] = strength * base * (0.7f + 0.6f * rv(104 + i * 13))
            val tint = leakTints[(rv(105 + i * 13) * 3.99f).toInt()]
            leaks[o + 4] = tint[0]
            leaks[o + 5] = tint[1]
            leaks[o + 6] = tint[2]
        }

        // Блики вдоль оси «яркое пятно → центр» (если кадр достаточно яркий).
        var bestL = -1f
        var bestI = 0
        val gp = glow.pixels
        for (i in gp.indices) {
            val c = gp[i]
            val l = LUMA_R * ((c shr 16) and 0xFF) + LUMA_G * ((c shr 8) and 0xFF) + LUMA_B * (c and 0xFF)
            if (l > bestL) { bestL = l; bestI = i }
        }
        val bpL = bestL * INV255
        var discs = FloatArray(0)
        var discCount = 0
        if (bpL > 0.55f) {
            val au = (bestI % glow.width + 0.5f) / glow.width
            val av = (bestI / glow.width + 0.5f) / glow.height * aspectH
            val dx = 0.5f - au
            val dy = 0.5f * aspectH - av
            val discTints = arrayOf(
                floatArrayOf(1.0f, 0.92f, 0.78f),  // тёпло-белый
                floatArrayOf(0.85f, 0.95f, 1.0f),  // холодный «призрак»
                floatArrayOf(1.0f, 0.78f, 0.5f),   // янтарный
            )
            discCount = 1 + (rv(20) * 3.99f).toInt()
            discs = FloatArray(4 * 7)
            for (i in 0 until discCount) {
                val o = i * 7
                val k = 0.3f + 1.3f * rv(201 + i * 9)
                discs[o] = au + dx * k
                discs[o + 1] = av + dy * k
                discs[o + 2] = 0.02f + 0.05f * rv(202 + i * 9)
                val amp = (0.32f - i * 0.06f).coerceAtLeast(0.08f)
                discs[o + 3] = strength * amp * bpL * (0.7f + 0.6f * rv(203 + i * 9))
                val tint = discTints[(rv(204 + i * 9) * 2.99f).toInt()]
                discs[o + 4] = tint[0]
                discs[o + 5] = tint[1]
                discs[o + 6] = tint[2]
            }
        }
        return ArtifactPlan(leaks, leakCount, discs, discCount)
    }

    private companion object {
        const val INV255 = 1f / 255f
        const val LUMA_R = 0.2126f
        const val LUMA_G = 0.7152f
        const val LUMA_B = 0.0722f

        fun smoothstep(e0: Float, e1: Float, x: Float): Float {
            if (x <= e0) return 0f
            if (x >= e1) return 1f
            val t = (x - e0) / (e1 - e0)
            return t * t * (3f - 2f * t)
        }

        /** Детерминированный шум -1..1 для зерна (целочисленный хеш). */
        fun grainNoise(x: Int, y: Int): Float {
            var h = x * -1640531535 + y * 668265263
            h = (h xor (h ushr 15)) * -2048294777
            h = (h xor (h ushr 13)) * -1028477379
            h = h xor (h ushr 16)
            return (h and 0xFFFF) / 32767.5f - 1f
        }

        /** Билинейная выборка ARGB-пикселя (альфа непрозрачная). */
        fun bilinear(img: PixelImage, x: Float, y: Float): Int {
            val fx = x - 0.5f
            val fy = y - 0.5f
            val x0 = fx.toInt(); val y0 = fy.toInt()
            val tx = fx - x0
            val ty = fy - y0
            val xc0 = x0.coerceIn(0, img.width - 1)
            val xc1 = (x0 + 1).coerceIn(0, img.width - 1)
            val yc0 = y0.coerceIn(0, img.height - 1)
            val yc1 = (y0 + 1).coerceIn(0, img.height - 1)
            val p = img.pixels
            val w = img.width

            val c00 = p[yc0 * w + xc0]
            val c10 = p[yc0 * w + xc1]
            val c01 = p[yc1 * w + xc0]
            val c11 = p[yc1 * w + xc1]

            val r0 = (c00 shr 16) and 0xFF
            val g0 = (c00 shr 8) and 0xFF
            val b0 = c00 and 0xFF
            val r1 = (c10 shr 16) and 0xFF
            val g1 = (c10 shr 8) and 0xFF
            val b1 = c10 and 0xFF
            val r2_ = (c01 shr 16) and 0xFF
            val g2 = (c01 shr 8) and 0xFF
            val b2 = c01 and 0xFF
            val r3 = (c11 shr 16) and 0xFF
            val g3 = (c11 shr 8) and 0xFF
            val b3 = c11 and 0xFF

            val topR = r0 + ((r1 - r0) * tx).toInt()
            val topG = g0 + ((g1 - g0) * tx).toInt()
            val topB = b0 + ((b1 - b0) * tx).toInt()
            val botR = r2_ + ((r3 - r2_) * tx).toInt()
            val botG = g2 + ((g3 - g2) * tx).toInt()
            val botB = b2 + ((b3 - b2) * tx).toInt()

            val r = topR + ((botR - topR) * ty).toInt()
            val g = topG + ((botG - topG) * ty).toInt()
            val b = topB + ((botB - topB) * ty).toInt()
            return 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }
    }
}

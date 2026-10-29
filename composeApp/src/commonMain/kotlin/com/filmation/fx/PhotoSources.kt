package com.filmation.fx

/**
 * Загруженное фото в трёх представлениях:
 *  - [export] — рабочий оригинал (до [MAX_EXPORT_SIDE] px) для финального рендера;
 *  - [preview] — уменьшенная копия для интерактивного превью;
 *  - [glow] — маленькая размытая копия (низкие частоты) для bloom/халации.
 */
class PhotoSources(val export: PixelImage) {
    val salt: Int = kotlin.random.Random.Default.nextInt(1, Int.MAX_VALUE)
    val preview: PixelImage = export.scaledTo(PREVIEW_SIDE)
    val glow: PixelImage = export.scaledTo(GLOW_SIDE)

    companion object {
        const val PREVIEW_SIDE = 1024
        const val GLOW_SIDE = 260
    }
}

/** Максимальная длинная сторона импортируемого/экспортируемого изображения, px. */
const val MAX_EXPORT_SIDE = 4096

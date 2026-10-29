package com.filmation.fx

import androidx.compose.runtime.Immutable

/**
 * Все параметры эмуляции плёнки. Значения крутятся слайдерами в редакторе.
 */
@Immutable
data class FilmParams(
    val exposure: Float = 0f,          // -2..2, EV
    val curve: Float = 0.2f,           // 0..1, плёночная S-кривая контраста
    val fade: Float = 0f,              // 0..1, матовые (поднятые) тени
    val mono: Boolean = false,         // чёрно-белый режим
    val grain: Float = 0.3f,           // 0..1, количество зерна
    val grainSize: Float = 1.2f,       // 0.5..3, размер зерна
    val halation: Float = 0.25f,       // 0..1, сила халации
    val halationRadius: Float = 0.5f,  // 0..1, радиус халации
    val bloom: Float = 0.15f,          // 0..1, сила свечения
    val bloomRadius: Float = 0.35f,    // 0..1, радиус свечения
    val vignette: Float = 0.2f,        // 0..1, виньетка
    val warm: Float = 0f,              // -1..1, света: тёплые(+) / холодные(-)
    val tint: Float = 0f,              // -1..1, тени: бирюза(+) / пурпур(-)
    val ca: Float = 0f,                // 0..1, хроматическая аберрация
    val artifacts: Float = 0f,         // 0..1, сила засветов/бликов
    val artifactVariant: Int = 0,      // 0..16, вид засветов (выбор раскладки)
    val seed: Int = 0,                 // зерно/артефакты: сид для "переброса"
) {
    companion object {
        /** Нейтральные параметры — практически без обработки. */
        val Neutral = FilmParams(
            exposure = 0f, curve = 0f, fade = 0f, mono = false,
            grain = 0f, grainSize = 1f,
            halation = 0f, halationRadius = 0.5f,
            bloom = 0f, bloomRadius = 0.35f,
            vignette = 0f, warm = 0f, tint = 0f, ca = 0f, artifacts = 0f, artifactVariant = 0,
        )
    }
}

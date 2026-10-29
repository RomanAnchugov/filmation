package com.filmation.fx

/**
 * Пресеты «плёнок»: стартовые точки, которые пользователь дальше крутит слайдерами.
 * Названия — оммажи характеру известных плёнок, не точная копия.
 */
data class FilmPreset(
    val id: String,
    val title: String,
    val params: FilmParams,
)

object FilmPresets {

    val all: List<FilmPreset> = listOf(
        FilmPreset(
            id = "portra", title = "Portra",
            // Мягкая портретная: тёплые света, низкий контраст, деликатное зерно
            FilmParams(
                exposure = 0.1f, curve = 0.35f, fade = 0.2f,
                grain = 0.22f, grainSize = 1.1f,
                halation = 0.18f, halationRadius = 0.5f,
                bloom = 0.12f, bloomRadius = 0.35f,
                vignette = 0.12f, warm = 0.35f, tint = 0.1f,
            ),
        ),
        FilmPreset(
            id = "gold", title = "Gold",
            // Тёплая «солнечная»: золотистые света
            FilmParams(
                exposure = 0.15f, curve = 0.45f, fade = 0.1f,
                grain = 0.28f, grainSize = 1.2f,
                halation = 0.3f, halationRadius = 0.55f,
                bloom = 0.2f, bloomRadius = 0.4f,
                vignette = 0.18f, warm = 0.6f, tint = -0.15f,
            ),
        ),
        FilmPreset(
            id = "cinestill", title = "CineStill",
            // Киношная с фирменной сильной халацией от красного слоя
            FilmParams(
                exposure = 0.05f, curve = 0.3f, fade = 0.12f,
                grain = 0.32f, grainSize = 1.3f,
                halation = 0.85f, halationRadius = 0.75f,
                bloom = 0.42f, bloomRadius = 0.45f,
                vignette = 0.2f, warm = 0.1f, tint = 0.3f, artifacts = 0.45f, artifactVariant = 3,
            ),
        ),
        FilmPreset(
            id = "trix", title = "Tri-X",
            // Ч/б репортажная: крупное зерно, плотные тени
            FilmParams(
                exposure = 0f, curve = 0.55f, fade = 0.05f, mono = true,
                grain = 0.7f, grainSize = 1.9f,
                halation = 0.1f, halationRadius = 0.5f,
                bloom = 0.05f, bloomRadius = 0.35f,
                vignette = 0.25f, warm = 0.15f, tint = 0f,
            ),
        ),
        FilmPreset(
            id = "superia", title = "Fuji",
            // Холодная зелёно-синяя
            FilmParams(
                exposure = 0f, curve = 0.4f, fade = 0.15f,
                grain = 0.3f, grainSize = 1.2f,
                halation = 0.22f, halationRadius = 0.45f,
                bloom = 0.15f, bloomRadius = 0.35f,
                vignette = 0.2f, warm = -0.3f, tint = 0.4f,
            ),
        ),
        FilmPreset(
            id = "crossprocess", title = "Cross",
            // Кросс-процесс: дикие цвета, контраст, сдвиг
            FilmParams(
                exposure = 0f, curve = 0.7f, fade = 0.08f,
                grain = 0.35f, grainSize = 1.3f,
                halation = 0.3f, halationRadius = 0.5f,
                bloom = 0.25f, bloomRadius = 0.4f,
                vignette = 0.28f, warm = 0.45f, tint = 0.55f, ca = 0.25f, artifacts = 0.25f, artifactVariant = 6,
            ),
        ),
        FilmPreset(
            id = "expired", title = "Expired",
            // Просроченная: выцветшие тени, пурпурный сдвиг, грубое зерно
            FilmParams(
                exposure = 0.05f, curve = 0.25f, fade = 0.55f,
                grain = 0.6f, grainSize = 1.6f,
                halation = 0.2f, halationRadius = 0.4f,
                bloom = 0.1f, bloomRadius = 0.35f,
                vignette = 0.3f, warm = -0.2f, tint = -0.45f, artifacts = 0.65f, artifactVariant = 9,
            ),
        ),
    )
}

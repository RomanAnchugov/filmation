package com.filmation.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmation.fx.FilmParams
import com.filmation.fx.FilmPresets
import com.filmation.fx.FilmRenderer
import com.filmation.fx.MAX_EXPORT_SIDE
import com.filmation.fx.PhotoSources
import com.filmation.fx.PixelImage
import com.filmation.photo.decodeToPixelImage
import com.filmation.photo.encodeJpeg
import com.filmation.photo.saveJpegToGallery
import com.filmation.photo.toImageBitmap
import com.filmation.ui.components.FilmPerforations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.TimeSource

/** Состояние pinch-to-zoom превью. Смещения — в пикселях контейнера. */
private class ZoomState {
    var scale by mutableStateOf(1f)
    var offsetX by mutableStateOf(0f)
    var offsetY by mutableStateOf(0f)
    var width by mutableStateOf(0f)
    var height by mutableStateOf(0f)

    /** Метка последнего жеста (монотонные часы) — для дебаунса перерендера под зум. */
    private var lastGestureAt = TimeSource.Monotonic.markNow()

    /** Мс с момента последнего жеста. */
    fun quietForMs(): Long = lastGestureAt.elapsedNow().inWholeMilliseconds

    fun applyGesture(centroid: Offset, pan: Offset, zoom: Float) {
        lastGestureAt = TimeSource.Monotonic.markNow()
        val old = scale
        val new = (old * zoom).coerceIn(1f, MAX_ZOOM)
        if (new <= 1.001f) {
            reset()
            return
        }
        // пан + коррекция точки фокуса: точка под пальцами остаётся на месте
        var nx = offsetX + pan.x
        var ny = offsetY + pan.y
        val k = new / old
        nx = centroid.x - (centroid.x - nx) * k
        ny = centroid.y - (centroid.y - ny) * k
        // не даём картинке улететь за пределы контейнера
        val maxX = width * (new - 1f) / 2f
        val maxY = height * (new - 1f) / 2f
        scale = new
        offsetX = nx.coerceIn(-maxX, maxX)
        offsetY = ny.coerceIn(-maxY, maxY)
    }

    fun reset() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        lastGestureAt = TimeSource.Monotonic.markNow()
    }

    companion object {
        const val MAX_ZOOM = 8f
    }
}

private class EditorUiState {
    var params by mutableStateOf(FilmParams())
    var presetId by mutableStateOf<String?>(null)
    var showOriginal by mutableStateOf(false)
    var exporting by mutableStateOf(false)

    /** Слайдер в процессе драга — рендерим превью в пониженном разрешении. */
    var dragging by mutableStateOf(false)

    /** Параметры, применённые к последнему показанному кадру (для прерывания устаревших рендеров). */
    fun effectiveParams(): FilmParams = if (showOriginal) FilmParams.Neutral else params

    /** Случайная (но осмысленная) плёнка. */
    fun randomize() {
        val rnd = Random.Default
        params = FilmParams(
            exposure = rnd.nextFloat(-0.4f, 0.4f),
            curve = rnd.nextFloat(0.25f, 0.7f),
            fade = rnd.nextFloat(0f, 0.5f),
            mono = rnd.nextFloat(0f, 1f) < 0.15f,
            grain = rnd.nextFloat(0.15f, 0.65f),
            grainSize = rnd.nextFloat(0.8f, 2f),
            halation = rnd.nextFloat(0f, 0.9f),
            halationRadius = rnd.nextFloat(0.3f, 0.8f),
            bloom = rnd.nextFloat(0f, 0.45f),
            bloomRadius = rnd.nextFloat(0.35f, 0.6f),
            vignette = rnd.nextFloat(0.05f, 0.45f),
            warm = rnd.nextFloat(-0.5f, 0.6f),
            tint = rnd.nextFloat(-0.4f, 0.4f),
            ca = rnd.nextFloat(0f, 0.25f),
            artifacts = rnd.nextFloat(0f, 0.75f),
            artifactVariant = rnd.nextInt(0, 17),
            seed = rnd.nextInt(0, 1000),
        )
        presetId = null
    }

    private fun Random.nextFloat(from: Float, to: Float) =
        from + (nextFloat()) * (to - from)
}

@Composable
fun EditorScreen(
    photoBytes: ByteArray,
    onClose: () -> Unit,
) {
    val state = remember { EditorUiState() }
    val zoom = remember { ZoomState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val renderer = remember { FilmRenderer() }

    // Декодирование и подготовка производных копий — один раз на фото.
    val sources by produceState<PhotoSources?>(initialValue = null, photoBytes) {
        value = withContext(Dispatchers.Default) {
            decodeToPixelImage(photoBytes, MAX_EXPORT_SIDE)?.let(::PhotoSources)
        }
    }

    val src = sources
    if (src == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Аспект фото фиксирует layout-размер превью: битмапы рендерятся в разных
    // разрешениях (драг 560px / статично 1080px+), но рамка остаётся неизменной.
    val photoAspect = src.preview.width.toFloat() / src.preview.height
    var previewBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Рендер-насос: опрашивает изменения не чаще ~60 Гц, рендерит свежий кадр,
    // а устаревшие (пользователь продолжил драг/смену параметров) прерывает
    // прямо в цикле рендера — тяжёлый рендер не блокирует отклик.
    LaunchedEffect(src) {
        var lastParams: FilmParams? = null
        var lastDragging = false
        var lastZoom = 0f
        val previewSide = maxOf(src.preview.width, src.preview.height)
        val exportSide = maxOf(src.export.width, src.export.height)

        while (isActive) {
            val p = state.effectiveParams()
            val draggingNow = state.dragging
            val zoomNow = zoom.scale
            // перерендер под зум — только когда жест успокоился (пинч не спамит рендерами)
            val zoomStale = zoomNow != lastZoom && zoom.quietForMs() >= ZOOM_SETTLE_MS
            val dirty = p != lastParams || draggingNow != lastDragging || zoomStale

            if (dirty) {
                val wanted = (
                    if (draggingNow) DRAG_PREVIEW_SIDE
                    else (BASE_PREVIEW_SIDE * zoomNow).toInt()
                    ).coerceAtLeast(320)
                // источник: preview-копия, а при сильном зуме — полный оригинал
                val source: PixelImage
                val srcSide: Int
                if (wanted > previewSide) {
                    source = src.export
                    srcSide = exportSide
                } else {
                    source = src.preview
                    srcSide = previewSide
                }
                val maxSide = minOf(wanted, srcSide, ZOOM_RENDER_CAP)
                val pw: Int
                val ph: Int
                if (photoAspect >= 1f) {
                    pw = maxSide
                    ph = (maxSide / photoAspect).toInt().coerceAtLeast(1)
                } else {
                    ph = maxSide
                    pw = (maxSide * photoAspect).toInt().coerceAtLeast(1)
                }

                val rendered = withContext(Dispatchers.Default) {
                    // прерываем рендер, если пользователь уже изменил параметры:
                    // тяжёлый кадр не должен блокировать интерактив
                    renderer.render(source, src.glow, p, pw, ph, src.salt) {
                        !isActive || state.effectiveParams() != p || state.dragging != draggingNow
                    }
                }
                if (rendered != null) {
                    runCatching { toImageBitmap(rendered) }.getOrNull()?.let {
                        previewBitmap = it
                    }
                }
                // фиксируем, что отрисовали (и при неудаче — чтобы не крутить цикл ошибок)
                lastParams = p
                lastDragging = draggingNow
                lastZoom = zoomNow
            }
            delay(16)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(Modifier.fillMaxSize()) {
            // --- Хедер: название + текущий «тип плёнки» ---
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "FILMATION",
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        state.presetId?.let { id ->
                            FilmPresets.all.firstOrNull { it.id == id }?.title?.uppercase() ?: "СВОЯ"
                        } ?: "СВОЯ ОБРАБОТКА",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { state.randomize() }) {
                    Icon(Icons.Filled.Casino, contentDescription = "Случайная плёнка")
                }
                IconButton(onClick = { state.showOriginal = !state.showOriginal }) {
                    Icon(
                        Icons.Filled.Flip,
                        contentDescription = "До/после",
                        tint = if (state.showOriginal) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { exportPhoto(renderer, src, state, scope, snackbar) },
                    enabled = !state.exporting,
                ) {
                    if (state.exporting) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Download, contentDescription = "Экспорт")
                    }
                }
            }

            // --- Превью в плёночной рамке: перфорация сверху и снизу, pinch-to-zoom ---
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                FilmPerforations(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged {
                            zoom.width = it.width.toFloat()
                            zoom.height = it.height.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { zoom.reset() })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                zoom.applyGesture(centroid, pan, gestureZoom)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = previewBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = "Превью с эффектами",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(photoAspect)
                                .graphicsLayer {
                                    scaleX = zoom.scale
                                    scaleY = zoom.scale
                                    translationX = zoom.offsetX
                                    translationY = zoom.offsetY
                                    transformOrigin = TransformOrigin(0f, 0f)
                                },
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        CircularProgressIndicator()
                    }
                }
                FilmPerforations(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                )
            }

            // --- Пресеты: «кассеты» ---
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(FilmPresets.all) { preset ->
                    PresetChip(
                        title = preset.title,
                        selected = state.presetId == preset.id,
                        onClick = {
                            state.presetId = preset.id
                            state.params = preset.params
                        },
                    )
                }
            }

            // --- Слайдеры: только параметры с заметным эффектом ---
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    // отступ от системного навбара: последний элемент не уезжает под него
                    bottom = navBarBottom + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item { SectionLabel("Свет") }
                item {
                    ParamSlider("Экспозиция", state.params.exposure, -2f..2f, state) { v ->
                        state.params = state.params.copy(exposure = v)
                    }
                }
                item {
                    ParamSlider("Свечение (bloom)", state.params.bloom, 0f..1f, state) { v ->
                        state.params = state.params.copy(bloom = v)
                    }
                }
                item {
                    ParamSlider("Халация", state.params.halation, 0f..1f, state) { v ->
                        state.params = state.params.copy(halation = v)
                    }
                }

                item { SectionLabel("Плёнка") }
                item {
                    ParamSlider("Контраст (S-кривая)", state.params.curve, 0f..1f, state) { v ->
                        state.params = state.params.copy(curve = v)
                    }
                }
                item {
                    ParamSlider("Матовые тени (fade)", state.params.fade, 0f..1f, state) { v ->
                        state.params = state.params.copy(fade = v)
                    }
                }
                item {
                    ParamSlider("Света: тепло / холод", state.params.warm, -1f..1f, state) { v ->
                        state.params = state.params.copy(warm = v)
                    }
                }

                item { SectionLabel("Текстура") }
                item {
                    ParamSlider("Зерно", state.params.grain, 0f..1f, state) { v ->
                        state.params = state.params.copy(grain = v)
                    }
                }
                item {
                    ParamSlider("Виньетка", state.params.vignette, 0f..1f, state) { v ->
                        state.params = state.params.copy(vignette = v)
                    }
                }

                item { SectionLabel("Засветы и блики") }
                item {
                    ParamSlider(
                        "Вариант",
                        state.params.artifactVariant.toFloat(),
                        0f..16f,
                        state,
                        steps = 15,
                        format = { it.toInt().toString() },
                    ) { v ->
                        state.params = state.params.copy(artifactVariant = v.toInt())
                    }
                }
                item {
                    ParamSlider("Сила", state.params.artifacts, 0f..1f, state) { v ->
                        state.params = state.params.copy(artifacts = v)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = navBarBottom + 16.dp),
        )
    }
}

/** Разрешение превью во время драга слайдера (быстрый отклик). */
private const val DRAG_PREVIEW_SIDE = 560

/** Базовое разрешение статичного превью (без зума). */
private const val BASE_PREVIEW_SIDE = 1080

/** Потолок разрешения рендера при зуме (баланс детальность/память). */
private const val ZOOM_RENDER_CAP = 3200

/** Сколько мс жест зума должен «успокоиться», прежде чем перерендеривать детально. */
private const val ZOOM_SETTLE_MS = 250L

private fun exportPhoto(
    renderer: FilmRenderer,
    src: PhotoSources,
    state: EditorUiState,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
) {
    if (state.exporting) return
    state.exporting = true
    scope.launch {
        val jpeg = withContext(Dispatchers.Default) {
            runCatching {
                val rendered = renderer.render(
                    src.export, src.glow, state.params,
                    src.export.width, src.export.height,
                    salt = src.salt,
                ) ?: return@runCatching null
                encodeJpeg(rendered, 95)
            }.getOrNull()
        }
        val saved = jpeg != null && runCatching { saveJpegToGallery(jpeg) }.getOrDefault(false)
        state.exporting = false
        snackbar.showSnackbar(if (saved) "Сохранено в галерею" else "Не удалось сохранить")
    }
}

@Composable
private fun PresetChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(4.dp),
                    )
                },
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    state: EditorUiState,
    steps: Int = 0,
    format: ((Float) -> String)? = null,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                format?.invoke(value) ?: formatValue(value),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = {
                state.dragging = true
                onChange(it)
            },
            onValueChangeFinished = { state.dragging = false },
            valueRange = range,
            steps = if (steps > 0) steps else 0,
        )
    }
}

private fun formatValue(v: Float): String =
    (kotlin.math.round((v * 100).toDouble()) / 100.0).toString()

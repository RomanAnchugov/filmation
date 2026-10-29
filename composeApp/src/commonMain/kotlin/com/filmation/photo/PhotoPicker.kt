package com.filmation.photo

import androidx.compose.runtime.Composable

/**
 * Лаунчер системного пикера фото.
 *
 * Возвращает функцию, которая открывает платформенный выбор изображения.
 * По завершении вызывает [onPicked] с нормализованными байтами изображения
 * (EXIF-поворот применён, длинная сторона <= [MAX_SIDE]) либо с null, если выбор отменён.
 */
@Composable
expect fun rememberPhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit

/** Максимальная длинная сторона изображения после нормализации. */
const val MAX_SIDE: Int = 4096

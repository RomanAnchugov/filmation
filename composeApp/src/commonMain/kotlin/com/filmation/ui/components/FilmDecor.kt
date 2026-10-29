package com.filmation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Полоса плёночной перфорации (кадровые отверстия). Декоративный элемент «плёнки».
 */
@Composable
fun FilmPerforations(
    modifier: Modifier = Modifier,
    holeColor: Color = Color(0xFF3D3833),
) {
    Canvas(modifier) {
        val holes = 18
        val slot = size.width / holes
        val holeW = slot * 0.52f
        val holeH = size.height * 0.52f
        val corner = CornerRadius(holeW * 0.3f, holeH * 0.3f)
        val y = (size.height - holeH) / 2f
        for (i in 0 until holes) {
            drawRoundRect(
                color = holeColor,
                topLeft = Offset(i * slot + (slot - holeW) / 2f, y),
                size = Size(holeW, holeH),
                cornerRadius = corner,
            )
        }
    }
}

/**
 * Угловые скобки видоискателя вокруг контента. Придают «камерный» вид.
 */
@Composable
fun ViewfinderCorners(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE8B96A),
    thickness: Dp = 2.dp,
    length: Dp = 16.dp,
) {
    Canvas(modifier) {
        val t = thickness.toPx()
        val len = length.toPx()
        val w = size.width
        val h = size.height
        val c = color.copy(alpha = 0.85f)

        // левый верхний
        drawRect(c, Offset(0f, 0f), Size(len, t))
        drawRect(c, Offset(0f, 0f), Size(t, len))
        // правый верхний
        drawRect(c, Offset(w - len, 0f), Size(len, t))
        drawRect(c, Offset(w - t, 0f), Size(t, len))
        // левый нижний
        drawRect(c, Offset(0f, h - t), Size(len, t))
        drawRect(c, Offset(0f, h - len), Size(t, len))
        // правый нижний
        drawRect(c, Offset(w - len, h - t), Size(len, t))
        drawRect(c, Offset(w - t, h - len), Size(t, len))
    }
}

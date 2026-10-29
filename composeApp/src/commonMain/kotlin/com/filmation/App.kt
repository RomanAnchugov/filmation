package com.filmation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.filmation.ui.EditorScreen
import com.filmation.ui.HomeScreen

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE8B96A),
    onPrimary = Color(0xFF241A08),
    secondary = Color(0xFF9A8F7A),
    background = Color(0xFF121110),
    onBackground = Color(0xFFEDE8DF),
    surface = Color(0xFF1A1917),
    onSurface = Color(0xFFEDE8DF),
    surfaceVariant = Color(0xFF242220),
    onSurfaceVariant = Color(0xFFC4BDB0),
    outline = Color(0xFF55504A),
)

@Composable
fun App() {
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }

    MaterialTheme(colorScheme = DarkScheme) {
        val bytes = photoBytes
        if (bytes == null) {
            HomeScreen(
                onPhotoPicked = { photoBytes = it },
            )
        } else {
            EditorScreen(
                photoBytes = bytes,
                onClose = { photoBytes = null },
            )
        }
    }
}

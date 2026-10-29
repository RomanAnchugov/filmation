package com.filmation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filmation.photo.rememberPhotoPicker
import com.filmation.ui.components.FilmPerforations
import com.filmation.ui.components.ViewfinderCorners
import org.jetbrains.compose.resources.stringResource
import com.filmation.res.Res
import com.filmation.res.home_pick_photo
import com.filmation.res.home_privacy
import com.filmation.res.home_subtitle
import com.filmation.res.home_tagline

/**
 * Декоративная маркировка кассеты — часть визуального стиля, языкозависимой
 * нагрузки не несёт (как и названия плёнок в пресетах), поэтому не локализуется.
 */
private val CARTRIDGE_META_TOP = listOf("● 36 EXP", "ISO 200", "24A ▸ 25A")
private val CARTRIDGE_META_BOTTOM = listOf("DX 400", "35 mm", "PROCESS C-41")

@Composable
fun HomeScreen(
    onPhotoPicked: (ByteArray?) -> Unit,
) {
    val pickImage = rememberPhotoPicker(onPicked = onPhotoPicked)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // «шапка кассеты»: тех-мета плёнки
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CARTRIDGE_META_TOP.forEach { MetaText(it) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CARTRIDGE_META_BOTTOM.forEach { MetaText(it) }
        }

        Spacer(Modifier.height(28.dp))

        // герой: кадр в визире
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp),
        ) {
            ViewfinderCorners(Modifier.fillMaxSize())
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "FILM",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "ATION",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 10.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(Res.string.home_tagline),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.home_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { pickImage() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(Res.string.home_pick_photo),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.home_privacy),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.outline,
        )

        Spacer(Modifier.weight(1f))

        // плёночная перфорация снизу
        FilmPerforations(
            Modifier
                .fillMaxWidth()
                .height(18.dp),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

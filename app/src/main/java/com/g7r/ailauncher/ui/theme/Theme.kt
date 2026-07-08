package com.g7r.ailauncher.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Dark-only theme: the launcher always renders in dark mode regardless of the
 * device's light/dark system setting. Material You dynamic color drives the
 * accents, with neutral roles pinned to a pure black (#000000) base.
 */
@Composable
fun AILauncherTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        darkColorScheme()
    }
    val colorScheme = base.copy(
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF0B0B0B),
        surfaceVariant = Color(0xFF1C1C1E),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF101010),
        surfaceContainer = Color(0xFF151515),
        surfaceContainerHigh = Color(0xFF1D1D1D),
        surfaceContainerHighest = Color(0xFF262626)
    )
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

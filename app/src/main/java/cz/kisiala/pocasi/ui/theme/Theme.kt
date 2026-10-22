package cz.kisiala.pocasi.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

private val LocalWeatherColors = staticCompositionLocalOf { LightWeatherColors }

@Composable
fun PocasiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = appColorScheme(LocalContext.current, darkTheme, dynamicColor)
    val weatherColors = weatherColors(darkTheme)

    CompositionLocalProvider(LocalWeatherColors provides weatherColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/** Barevné schéma aplikace – dynamic color (Android 12+), jinak vlastní modré; používá ho i widget. */
fun appColorScheme(context: Context, darkTheme: Boolean, dynamicColor: Boolean = true): ColorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    darkTheme -> DarkColors
    else -> LightColors
}

fun weatherColors(darkTheme: Boolean): WeatherColors = if (darkTheme) DarkWeatherColors else LightWeatherColors

/** Přístup k vlastním barvám: `WeatherTheme.colors.temperature`. */
object WeatherTheme {
    val colors: WeatherColors
        @Composable get() = LocalWeatherColors.current
}

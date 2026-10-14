package cz.kisiala.pocasi.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
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
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    val weatherColors = if (darkTheme) DarkWeatherColors else LightWeatherColors

    CompositionLocalProvider(LocalWeatherColors provides weatherColors) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/** Přístup k vlastním barvám: `WeatherTheme.colors.temperature`. */
object WeatherTheme {
    val colors: WeatherColors
        @Composable get() = LocalWeatherColors.current
}

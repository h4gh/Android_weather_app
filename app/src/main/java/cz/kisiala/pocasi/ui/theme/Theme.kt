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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LocalWeatherColors = staticCompositionLocalOf { LightWeatherColors }
private val LocalBlackBackground = staticCompositionLocalOf { false }

@Composable
fun PocasiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    blackBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val black = darkTheme && blackBackground
    val colorScheme = appColorScheme(LocalContext.current, darkTheme, dynamicColor).let {
        // OLED: černé pozadí, karty i spodní lišta (karty oddělí tenký obrys, viz WeatherCard);
        // dialogy a menu (surfaceContainerHigh*) zůstávají šedé, aby nesplývaly s pozadím.
        if (black) {
            it.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceDim = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainer = Color.Black,
            )
        } else it
    }
    val weatherColors = weatherColors(darkTheme)

    CompositionLocalProvider(LocalWeatherColors provides weatherColors, LocalBlackBackground provides black) {
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

    /** Tmavý režim s čistě černým pozadím (OLED) – karty mají místo podkladu obrys. */
    val blackBackground: Boolean
        @Composable get() = LocalBlackBackground.current
}

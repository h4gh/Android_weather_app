package cz.kisiala.pocasi.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import cz.kisiala.pocasi.model.HourlyWeather
import cz.kisiala.pocasi.model.ThemeMode
import cz.kisiala.pocasi.model.WeatherIconType
import cz.kisiala.pocasi.ui.theme.appColorScheme
import cz.kisiala.pocasi.ui.theme.weatherColors
import cz.kisiala.pocasi.ui.today.MeteogramStyle
import cz.kisiala.pocasi.ui.today.WeatherIconColors
import cz.kisiala.pocasi.ui.today.drawMeteogram
import cz.kisiala.pocasi.ui.today.drawWeatherIcon
import kotlin.math.roundToInt

/** Barvy widgetu: stejné schéma jako aplikace (dynamic color), světlé/tmavé podle volby Vzhled. */
class WidgetColors(val scheme: ColorScheme, val temperature: Color) {
    /** Pozadí widgetu – tónovaná plocha jako karty v aplikaci. */
    val background: Color get() = scheme.surfaceContainerLow

    val icon = WeatherIconColors(
        sun = temperature,
        moon = scheme.onSurfaceVariant,
        cloud = scheme.onSurfaceVariant,
        cloudFill = scheme.surfaceContainerLow,
        drop = scheme.primary,
    )

    companion object {
        fun resolve(context: Context, mode: ThemeMode): WidgetColors {
            val systemDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val dark = mode.isDark(systemDark)
            return WidgetColors(appColorScheme(context, dark), weatherColors(dark).temperature)
        }
    }
}

/**
 * Widget (RemoteViews) neumí Compose Canvas – ikony a meteogram se kreslí stejným kódem
 * jako v aplikaci (DrawScope) do bitmapy.
 */
object WidgetDrawing {

    fun icon(context: Context, type: WeatherIconType, isDay: Boolean, colors: WidgetColors, size: Dp): Bitmap {
        val px = size.toPx(context)
        return render(context, px, px) { drawWeatherIcon(type, isDay, colors.icon) }
    }

    fun meteogram(context: Context, hours: List<HourlyWeather>, colors: WidgetColors, width: Dp, height: Dp): Bitmap {
        val scheme = colors.scheme
        val style = MeteogramStyle(
            temperature = colors.temperature,
            heavyPrecip = scheme.primary,
            lightPrecip = scheme.inversePrimary,
            grid = scheme.outlineVariant,
            axisText = TextStyle(fontSize = 11.sp, color = scheme.onSurfaceVariant),
            temperatureText = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = scheme.onSurface),
        )
        val measurer = TextMeasurer(createFontFamilyResolver(context), density(context), LayoutDirection.Ltr)
        return render(context, width.toPx(context), height.toPx(context)) {
            drawMeteogram(hours, style, measurer, markStart = true)
        }
    }

    private fun render(context: Context, width: Int, height: Int, draw: DrawScope.() -> Unit): Bitmap {
        val image = ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))
        CanvasDrawScope().draw(
            density(context),
            LayoutDirection.Ltr,
            Canvas(image),
            Size(image.width.toFloat(), image.height.toFloat()),
            draw,
        )
        return image.asAndroidBitmap()
    }

    private fun density(context: Context) =
        Density(context.resources.displayMetrics.density, context.resources.configuration.fontScale)

    private fun Dp.toPx(context: Context): Int = (value * context.resources.displayMetrics.density).roundToInt()
}

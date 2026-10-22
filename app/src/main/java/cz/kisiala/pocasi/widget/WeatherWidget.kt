package cz.kisiala.pocasi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.ui.today.formatTemperature
import cz.kisiala.pocasi.ui.today.label
import kotlinx.coroutines.flow.first

/**
 * Widget „Počasí“ přepínaný podle velikosti: malý (2×2) kompaktní, úzký vysoký kompaktní s meteogramem,
 * široký nízký jen aktuální stav v řádku,
 * široký vyšší (4×2) navíc meteogram na 24 h – stejný jako karta „Dalších 24 hodin“.
 */
class WeatherWidget : GlanceAppWidget() {

    // Exact: rozvržení podle skutečné velikosti, kterou hlásí launcher (některé launchery, např. Octopi,
    // neumí z Responsive sady vybrat a vezmou vždy nejmenší).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetUpdater.refreshIfStale(context, id)
        val themeModes = context.appContainer().settingsRepository.themeMode
        val initialMode = themeModes.first()
        provideContent {
            WidgetFrame(themeModes, initialMode) { data, colors ->
                val size = LocalSize.current
                when {
                    size.width < WIDE.width && size.height < WIDE_TALL.height -> CompactLayout(data, colors)
                    size.width < WIDE.width -> CompactWithMeteogram(data, colors)
                    size.height < WIDE_TALL.height -> WideLayout(data, colors)
                    else -> WideWithMeteogram(data, colors)
                }
            }
        }
    }

    companion object {
        private val WIDE = DpSize(250.dp, 80.dp)
        private val WIDE_TALL = DpSize(250.dp, 170.dp)
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

@Composable
private fun CompactLayout(data: WidgetData, colors: WidgetColors) {
    val context = LocalContext.current
    val scheme = colors.scheme
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        PlaceLabel(data, colors)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(formatTemperature(data.temperature), style = textStyle(scheme.onSurface, 52.sp))
            Spacer(GlanceModifier.defaultWeight())
            WidgetWeatherIcon(data.weatherCondition, data.isDay, colors, 44.dp)
        }
        Spacer(GlanceModifier.defaultWeight())
        Text(
            context.getString(data.weatherCondition.label),
            style = textStyle(scheme.onSurface, 15.sp, FontWeight.Medium),
            maxLines = 1,
        )
        MaxMin(data, colors)
    }
}

@Composable
private fun WideLayout(data: WidgetData, colors: WidgetColors) {
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CurrentRow(data, colors)
    }
}

/** Úzký, ale vysoký widget (např. 2×2 na launcheru s jemnou mřížkou): kompaktní hlavička + meteogram. */
@Composable
private fun CompactWithMeteogram(data: WidgetData, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val scheme = colors.scheme
    val horizontalPadding = 16.dp
    Column(GlanceModifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 14.dp)) {
        PlaceLabel(data, colors)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTemperature(data.temperature), style = textStyle(scheme.onSurface, 44.sp))
            Spacer(GlanceModifier.defaultWeight())
            WidgetWeatherIcon(data.weatherCondition, data.isDay, colors, 40.dp)
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                context.getString(data.weatherCondition.label),
                style = textStyle(scheme.onSurface, 14.sp, FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            MaxMin(data, colors)
        }
        Spacer(GlanceModifier.defaultWeight())
        val chartWidth = size.width - horizontalPadding * 2
        val chartHeight = (size.height - 128.dp).coerceIn(64.dp, 140.dp)
        Image(
            ImageProvider(WidgetDrawing.meteogram(context, data.hourlyWeather(), colors, chartWidth, chartHeight)),
            contentDescription = context.getString(R.string.next24_title),
            modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
        )
    }
}

@Composable
private fun WideWithMeteogram(data: WidgetData, colors: WidgetColors) {
    val context = LocalContext.current
    val size = LocalSize.current
    val horizontalPadding = 18.dp
    Column(GlanceModifier.fillMaxSize().padding(horizontal = horizontalPadding, vertical = 12.dp)) {
        CurrentRow(data, colors)
        Spacer(GlanceModifier.height(6.dp))
        // Meteogram vyplní zbytek výšky; bitmapa v přesné velikosti, ať je ostrá.
        val chartWidth = size.width - horizontalPadding * 2
        val chartHeight = (size.height - 92.dp).coerceIn(72.dp, 140.dp)
        Image(
            ImageProvider(WidgetDrawing.meteogram(context, data.hourlyWeather(), colors, chartWidth, chartHeight)),
            contentDescription = context.getString(R.string.next24_title),
            modifier = GlanceModifier.fillMaxWidth().height(chartHeight),
        )
    }
}

/** Aktuální stav v řádku: teplota, popis a místo vlevo, max/min a ikona vpravo. */
@Composable
private fun CurrentRow(data: WidgetData, colors: WidgetColors) {
    val context = LocalContext.current
    val scheme = colors.scheme
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatTemperature(data.temperature), style = textStyle(scheme.onSurface, 44.sp))
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                context.getString(data.weatherCondition.label),
                style = textStyle(scheme.onSurface, 15.sp, FontWeight.Medium),
                maxLines = 1,
            )
            PlaceLabel(data, colors)
        }
        Column(horizontalAlignment = Alignment.End) {
            MaxMin(data, colors, stacked = true)
        }
        Spacer(GlanceModifier.width(8.dp))
        WidgetWeatherIcon(data.weatherCondition, data.isDay, colors, 40.dp)
    }
}

/** „13° max  3° min“ – maximum teplou barvou jako v aplikaci. */
@Composable
private fun MaxMin(data: WidgetData, colors: WidgetColors, stacked: Boolean = false) {
    val context = LocalContext.current
    val scheme = colors.scheme
    val max = @Composable {
        Row {
            Text(formatTemperature(data.max), style = textStyle(colors.temperature, 13.sp, FontWeight.Bold))
            Text(" " + context.getString(R.string.widget_max), style = textStyle(scheme.onSurfaceVariant, 13.sp))
        }
    }
    val min = @Composable {
        Text(
            formatTemperature(data.min) + " " + context.getString(R.string.widget_min),
            style = textStyle(scheme.onSurfaceVariant, 13.sp),
        )
    }
    if (stacked) {
        Column(horizontalAlignment = Alignment.End) {
            max()
            min()
        }
    } else {
        Row {
            max()
            Spacer(GlanceModifier.width(10.dp))
            min()
        }
    }
}

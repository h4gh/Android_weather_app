package cz.kisiala.pocasi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
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
import androidx.glance.text.TextAlign
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.ui.today.formatPrecipitation
import cz.kisiala.pocasi.ui.today.formatTemperature
import cz.kisiala.pocasi.ui.today.label
import kotlinx.coroutines.flow.first

/** Widget „Výhled na 5 dní“: krátce aktuální stav a pod ním dny od zítřka (ikona, max/min, srážky). */
class OutlookWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetUpdater.refreshIfStale(context, id)
        val themeModes = context.appContainer().settingsRepository.themeMode
        val initialMode = themeModes.first()
        provideContent {
            WidgetFrame(themeModes, initialMode) { data, colors -> OutlookLayout(data, colors) }
        }
    }
}

class OutlookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = OutlookWidget()
}

@Composable
private fun OutlookLayout(data: WidgetData, colors: WidgetColors) {
    val context = LocalContext.current
    val scheme = colors.scheme
    // Nízký widget (4×1,5): bez řádku srážek, ať se dny vejdou.
    val showPrecipitation = LocalSize.current.height >= 160.dp
    val dayNames = context.resources.getStringArray(R.array.day_short)

    Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTemperature(data.temperature), style = textStyle(scheme.onSurface, 26.sp))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                context.getString(data.weatherCondition.label),
                style = textStyle(scheme.onSurface, 14.sp, FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            PlaceLabel(data, colors)
        }
        Spacer(GlanceModifier.height(8.dp))
        Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant))
        Spacer(GlanceModifier.defaultWeight())
        Row(GlanceModifier.fillMaxWidth()) {
            data.days.forEachIndexed { index, day ->
                val hasPrecipitation = day.precipitation >= 0.05
                Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (index == 0) context.getString(R.string.outlook_tomorrow) else dayNames[day.localDate.dayOfWeek.value - 1],
                        style = textStyle(scheme.onSurface, 13.sp, FontWeight.Bold, TextAlign.Center),
                        maxLines = 1,
                    )
                    WidgetWeatherIcon(day.weatherCondition, isDay = true, colors = colors, size = 30.dp)
                    Text(formatTemperature(day.max), style = textStyle(scheme.onSurface, 15.sp, FontWeight.Bold))
                    Text(formatTemperature(day.min), style = textStyle(scheme.onSurfaceVariant, 13.sp))
                    if (showPrecipitation) {
                        Text(
                            if (hasPrecipitation) context.getString(R.string.outlook_precip_mm, formatPrecipitation(day.precipitation)) else " ",
                            style = textStyle(scheme.primary, 12.sp, FontWeight.Medium),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(GlanceModifier.defaultWeight())
    }
}

package cz.kisiala.pocasi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.Spacer
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import cz.kisiala.pocasi.MainActivity
import cz.kisiala.pocasi.PocasiApp
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.ThemeMode
import cz.kisiala.pocasi.model.WeatherCondition
import cz.kisiala.pocasi.ui.today.label
import kotlinx.coroutines.flow.Flow

/**
 * Společný rámec obou widgetů: barvy podle volby Vzhled (živě i při změně v aplikaci), pozadí se
 * systémovými rohy, klepnutí otevře aplikaci a stavy „nenastaveno / načítám / chyba“.
 */
@Composable
fun WidgetFrame(
    themeModes: Flow<ThemeMode>,
    initialMode: ThemeMode,
    content: @Composable (data: WidgetData, colors: WidgetColors) -> Unit,
) {
    val context = LocalContext.current
    val mode by themeModes.collectAsState(initialMode)
    val colors = WidgetColors.resolve(context, mode)
    val prefs = currentState<Preferences>()
    val data = WidgetState.data(prefs)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(colors.background)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        when {
            data != null -> content(data, colors)
            prefs[WidgetState.PLACE] == null -> WidgetMessage(context.getString(R.string.widget_not_configured), colors)
            prefs[WidgetState.FAILED] == true -> WidgetMessage(context.getString(R.string.widget_error), colors)
            else -> WidgetMessage(context.getString(R.string.widget_loading), colors)
        }
    }
}

@Composable
private fun WidgetMessage(text: String, colors: WidgetColors) {
    Box(GlanceModifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(text, style = textStyle(colors.scheme.onSurfaceVariant, 14.sp, textAlign = TextAlign.Center))
    }
}

/** Název místa, u polohy s šipkou (jako „Moje poloha“ v aplikaci). */
@Composable
fun PlaceLabel(data: WidgetData, colors: WidgetColors, fontSize: TextUnit = 13.sp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (data.isCurrentLocation) {
            Image(
                ImageProvider(R.drawable.ic_widget_location),
                contentDescription = LocalContext.current.getString(R.string.place_my_location),
                colorFilter = ColorFilter.tint(ColorProvider(colors.scheme.onSurfaceVariant)),
                modifier = GlanceModifier.size(12.dp),
            )
            Spacer(GlanceModifier.width(4.dp))
        }
        Text(data.placeName, style = textStyle(colors.scheme.onSurfaceVariant, fontSize, FontWeight.Medium), maxLines = 1)
    }
}

/** Ikona počasí jako bitmapa ze sdíleného kódu aplikace. */
@Composable
fun WidgetWeatherIcon(condition: WeatherCondition, isDay: Boolean, colors: WidgetColors, size: Dp) {
    val context = LocalContext.current
    Image(
        ImageProvider(WidgetDrawing.icon(context, condition.icon, isDay, colors, size)),
        contentDescription = context.getString(condition.label),
        modifier = GlanceModifier.size(size),
    )
}

fun textStyle(
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign? = null,
) = TextStyle(color = ColorProvider(color), fontSize = fontSize, fontWeight = fontWeight, textAlign = textAlign)


fun Context.appContainer() = (applicationContext as PocasiApp).container

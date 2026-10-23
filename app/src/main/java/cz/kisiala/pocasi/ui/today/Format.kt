package cz.kisiala.pocasi.ui.today

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.PressureTrend
import cz.kisiala.pocasi.model.WeatherCondition
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.roundToInt

/** Od této rychlosti (km/h) zvýrazňujeme vítr. */
const val STRONG_WIND_KMH = 20

fun formatTemperature(celsius: Double): String = "${celsius.roundToInt()}°"

/**
 * Srážky v mm s desetinným oddělovačem jazyka aplikace: „4,2“ (anglicky „4.2“), celá čísla bez desetin („2“, „0“).
 */
fun formatPrecipitation(mm: Double): String {
    val tenths = (mm * 10).roundToInt()
    val separator = DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator
    return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}$separator${tenths % 10}"
}

/** Směr, odkud vítr vane (S, SV, V, JV, J, JZ, Z, SZ; anglicky N, NE, …). */
@Composable
fun windDirectionName(degrees: Int): String =
    stringArrayResource(R.array.wind_direction_short)[(((degrees % 360) + 360) % 360 + 22) / 45 % 8]

@get:StringRes
val WeatherCondition.label: Int
    get() = when (this) {
        WeatherCondition.Clear -> R.string.cond_clear
        WeatherCondition.MostlyClear -> R.string.cond_mostly_clear
        WeatherCondition.PartlyCloudy -> R.string.cond_partly_cloudy
        WeatherCondition.MostlyCloudy -> R.string.cond_mostly_cloudy
        WeatherCondition.Overcast -> R.string.cond_overcast
        WeatherCondition.LightRain -> R.string.cond_light_rain
        WeatherCondition.Rain -> R.string.cond_rain
        WeatherCondition.HeavyRain -> R.string.cond_heavy_rain
        WeatherCondition.Snow -> R.string.cond_snow
    }

@get:StringRes
val PressureTrend.label: Int
    get() = when (this) {
        PressureTrend.Rising -> R.string.trend_rising
        PressureTrend.Falling -> R.string.trend_falling
        PressureTrend.Steady -> R.string.trend_steady
    }

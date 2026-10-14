package cz.kisiala.pocasi.ui.today

import androidx.annotation.StringRes
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.PressureTrend
import cz.kisiala.pocasi.model.WeatherCondition
import kotlin.math.roundToInt

/** Od této rychlosti (km/h) zvýrazňujeme vítr. */
const val STRONG_WIND_KMH = 20

fun formatTemperature(celsius: Double): String = "${celsius.roundToInt()}°"

/** Směr, odkud vítr vane, česky (S, SV, V, JV, J, JZ, Z, SZ). */
fun windDirectionName(degrees: Int): String {
    val names = arrayOf("S", "SV", "V", "JV", "J", "JZ", "Z", "SZ")
    return names[(((degrees % 360) + 360) % 360 + 22) / 45 % 8]
}

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

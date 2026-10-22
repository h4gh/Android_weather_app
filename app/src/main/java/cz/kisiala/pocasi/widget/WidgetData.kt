package cz.kisiala.pocasi.widget

import cz.kisiala.pocasi.model.DailyWeather
import cz.kisiala.pocasi.model.Forecast
import cz.kisiala.pocasi.model.HourlyWeather
import cz.kisiala.pocasi.model.WeatherCondition
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Výřez předpovědi pro widget – ukládá se do stavu widgetu (JSON), aby při výpadku sítě
 * zůstala poslední data a widget se mohl překreslit bez nového dotazu.
 */
@Serializable
data class WidgetData(
    val placeName: String,
    val isCurrentLocation: Boolean,
    val temperature: Double,
    val condition: String,
    val isDay: Boolean,
    val max: Double,
    val min: Double,
    /** Teď … +24 h po hodinách (25 hodnot) pro meteogram. */
    val hours: List<WidgetHour>,
    /** Výhled od zítřka. */
    val days: List<WidgetDay>,
) {
    val weatherCondition: WeatherCondition
        get() = WeatherCondition.entries.firstOrNull { it.name == condition } ?: WeatherCondition.PartlyCloudy

    /** Hodiny ve tvaru, se kterým pracuje sdílený meteogram (ostatní hodnoty widget nepotřebuje). */
    fun hourlyWeather(): List<HourlyWeather> = hours.map {
        HourlyWeather(
            time = LocalDateTime.parse(it.time),
            temperature = it.temperature,
            apparentTemperature = null,
            humidity = null,
            dewPoint = null,
            precipitation = it.precipitation,
            cloudCover = null,
            pressure = null,
            windSpeed = 0.0,
            windDirection = 0,
            windGusts = null,
            isDay = true,
        )
    }

    companion object {
        const val OUTLOOK_DAYS = 5

        fun from(forecast: Forecast, placeName: String, isCurrentLocation: Boolean) = WidgetData(
            placeName = placeName,
            isCurrentLocation = isCurrentLocation,
            temperature = forecast.current.temperature,
            condition = forecast.current.condition.name,
            isDay = forecast.current.isDay,
            max = forecast.todayMax,
            min = forecast.todayMin,
            hours = forecast.next(24).map { WidgetHour(it.time.toString(), it.temperature, it.precipitation) },
            days = forecast.outlook(OUTLOOK_DAYS).map { it.toWidgetDay() },
        )

        private fun DailyWeather.toWidgetDay() = WidgetDay(
            date = date.toString(),
            max = temperatureMax,
            min = temperatureMin,
            precipitation = precipitationSum,
            condition = condition.name,
        )
    }
}

@Serializable
data class WidgetHour(val time: String, val temperature: Double, val precipitation: Double)

@Serializable
data class WidgetDay(
    val date: String,
    val max: Double,
    val min: Double,
    val precipitation: Double,
    val condition: String,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)

    val weatherCondition: WeatherCondition
        get() = WeatherCondition.entries.firstOrNull { it.name == condition } ?: WeatherCondition.PartlyCloudy
}

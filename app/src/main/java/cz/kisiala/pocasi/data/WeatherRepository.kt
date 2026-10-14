package cz.kisiala.pocasi.data

import cz.kisiala.pocasi.data.remote.ForecastResponse
import cz.kisiala.pocasi.data.remote.OpenMeteoApi
import cz.kisiala.pocasi.model.CurrentWeather
import cz.kisiala.pocasi.model.DailyWeather
import cz.kisiala.pocasi.model.Forecast
import cz.kisiala.pocasi.model.HourlyWeather
import cz.kisiala.pocasi.model.Place
import java.time.LocalDate
import java.time.LocalDateTime

class WeatherRepository(private val api: OpenMeteoApi) {

    suspend fun forecast(place: Place): Forecast =
        api.forecast(latitude = place.latitude, longitude = place.longitude).toForecast()
}

internal fun ForecastResponse.toForecast(): Forecast {
    val c = current
    val currentWeather = CurrentWeather(
        time = LocalDateTime.parse(c.time),
        temperature = c.temperature,
        apparentTemperature = c.apparentTemperature,
        humidity = c.humidity,
        dewPoint = c.dewPoint,
        precipitation = c.precipitation,
        cloudCover = c.cloudCover,
        pressure = c.pressure,
        windSpeed = c.windSpeed,
        windDirection = c.windDirection,
        windGusts = c.windGusts,
        isDay = c.isDay == 1,
    )
    val h = hourly
    val hours = h.time.indices.mapNotNull { i ->
        // Hodiny bez teploty (za koncem dosahu modelu) vynecháme.
        val temperature = h.temperature.getOrNull(i) ?: return@mapNotNull null
        HourlyWeather(
            time = LocalDateTime.parse(h.time[i]),
            temperature = temperature,
            apparentTemperature = h.apparentTemperature.getOrNull(i),
            humidity = h.humidity.getOrNull(i),
            dewPoint = h.dewPoint.getOrNull(i),
            precipitation = h.precipitation.getOrNull(i) ?: 0.0,
            cloudCover = h.cloudCover.getOrNull(i),
            pressure = h.pressure.getOrNull(i),
            windSpeed = h.windSpeed.getOrNull(i) ?: 0.0,
            windDirection = h.windDirection.getOrNull(i) ?: 0,
            windGusts = h.windGusts.getOrNull(i),
            isDay = h.isDay.getOrNull(i) != 0,
        )
    }
    val d = daily
    val days = d?.time?.indices?.mapNotNull { i ->
        val date = LocalDate.parse(d.time[i])
        val max = d.temperatureMax.getOrNull(i) ?: return@mapNotNull null
        val min = d.temperatureMin.getOrNull(i) ?: return@mapNotNull null
        DailyWeather(
            date = date,
            temperatureMax = max,
            temperatureMin = min,
            precipitationSum = d.precipitationSum.getOrNull(i) ?: 0.0,
            precipitationProbability = d.precipitationProbabilityMax.getOrNull(i),
            windSpeedMax = d.windSpeedMax.getOrNull(i) ?: 0.0,
            windGustsMax = d.windGustsMax.getOrNull(i),
            windDirectionDominant = d.windDirectionDominant.getOrNull(i) ?: 0,
            hours = hours.filter { it.time.toLocalDate() == date },
        )
    }.orEmpty()
    return Forecast(current = currentWeather, hourly = hours, daily = days)
}

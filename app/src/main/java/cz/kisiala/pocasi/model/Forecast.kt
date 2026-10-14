package cz.kisiala.pocasi.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Předpověď pro jedno místo, převedená z odpovědi Open-Meteo. Časy jsou v místním čase místa. */
data class Forecast(
    val current: CurrentWeather,
    /** Všechny hodinové hodnoty (od půlnoci dneška, 3 dny dopředu). */
    val hourly: List<HourlyWeather>,
) {
    /** Index hodiny odpovídající aktuálnímu stavu. */
    private val currentHourIndex: Int by lazy {
        val hour = current.time.withMinute(0)
        hourly.indexOfFirst { it.time == hour }.takeIf { it >= 0 }
            ?: hourly.indexOfLast { it.time <= current.time }.coerceAtLeast(0)
    }

    /** Hodinové hodnoty od aktuální hodiny na [hours] hodin dopředu (včetně obou konců). */
    fun next(hours: Int): List<HourlyWeather> =
        hourly.subList(currentHourIndex.coerceAtMost(hourly.size), (currentHourIndex + hours + 1).coerceAtMost(hourly.size))

    private val today: List<HourlyWeather> by lazy {
        val date: LocalDate = current.time.toLocalDate()
        hourly.filter { it.time.toLocalDate() == date }
    }

    val todayMax: Double get() = today.maxOfOrNull { it.temperature } ?: current.temperature
    val todayMin: Double get() = today.minOfOrNull { it.temperature } ?: current.temperature

    /** Tendence tlaku za poslední 3 hodiny (příp. následující, pokud data zpětně chybí). */
    val pressureTrend: PressureTrend by lazy {
        val now = hourly.getOrNull(currentHourIndex)?.pressure ?: return@lazy PressureTrend.Steady
        val diff = hourly.getOrNull(currentHourIndex - 3)?.pressure?.let { now - it }
            ?: hourly.getOrNull(currentHourIndex + 3)?.pressure?.let { it - now }
            ?: 0.0
        when {
            diff >= PRESSURE_TREND_THRESHOLD -> PressureTrend.Rising
            diff <= -PRESSURE_TREND_THRESHOLD -> PressureTrend.Falling
            else -> PressureTrend.Steady
        }
    }

    private companion object {
        /** hPa za 3 hodiny – menší změnu bereme jako stálý tlak. */
        const val PRESSURE_TREND_THRESHOLD = 1.0
    }
}

data class CurrentWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int,
    val dewPoint: Double,
    val precipitation: Double,
    val cloudCover: Int,
    val pressure: Double,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double,
    val isDay: Boolean,
) {
    val condition: WeatherCondition
        get() = WeatherCondition.from(cloudCover, precipitation, temperature)
}

data class HourlyWeather(
    val time: LocalDateTime,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    /** Úhrn srážek za předchozí hodinu (mm). */
    val precipitation: Double,
    val cloudCover: Int?,
    val pressure: Double?,
    val windSpeed: Double,
    val windDirection: Int,
    val windGusts: Double?,
    val isDay: Boolean,
) {
    val condition: WeatherCondition
        get() = WeatherCondition.from(cloudCover ?: 0, precipitation, temperature)
}

enum class PressureTrend { Rising, Falling, Steady }

/**
 * Stav počasí odvozený z oblačnosti a srážek – model ALADIN nedává symbol počasí.
 * Hranice oblačnosti odpovídají slovníku ČHMÚ (jasno … zataženo, po osminách).
 */
enum class WeatherCondition(val icon: WeatherIconType) {
    Clear(WeatherIconType.Clear),
    MostlyClear(WeatherIconType.Clear),
    PartlyCloudy(WeatherIconType.PartlyCloudy),
    MostlyCloudy(WeatherIconType.PartlyCloudy),
    Overcast(WeatherIconType.Cloudy),
    LightRain(WeatherIconType.Rain),
    Rain(WeatherIconType.Rain),
    HeavyRain(WeatherIconType.Rain),
    Snow(WeatherIconType.Snow);

    companion object {
        fun from(cloudCover: Int, precipitation: Double, temperature: Double): WeatherCondition = when {
            precipitation >= 0.1 && temperature <= 1.0 -> Snow
            precipitation >= 4.0 -> HeavyRain
            precipitation >= 1.0 -> Rain
            precipitation >= 0.1 -> LightRain
            cloudCover < 13 -> Clear
            cloudCover < 38 -> MostlyClear
            cloudCover < 63 -> PartlyCloudy
            cloudCover < 88 -> MostlyCloudy
            else -> Overcast
        }
    }
}

enum class WeatherIconType { Clear, PartlyCloudy, Cloudy, Rain, Snow }

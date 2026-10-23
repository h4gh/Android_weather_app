package cz.kisiala.pocasi.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val timezone: String? = null,
    val current: CurrentDto,
    val hourly: HourlyDto,
    val daily: DailyDto? = null,
    @SerialName("minutely_15") val minutely15: Minutely15Dto? = null,
)

/** Srážky po 15 min; hodnota u času je úhrn za předchozích 15 minut. */
@Serializable
data class Minutely15Dto(
    val time: List<String>,
    val precipitation: List<Double?> = emptyList(),
)

@Serializable
data class CurrentDto(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    @SerialName("relative_humidity_2m") val humidity: Int,
    @SerialName("dew_point_2m") val dewPoint: Double,
    val precipitation: Double = 0.0,
    @SerialName("cloud_cover") val cloudCover: Int,
    @SerialName("pressure_msl") val pressure: Double,
    @SerialName("wind_speed_10m") val windSpeed: Double,
    @SerialName("wind_direction_10m") val windDirection: Int,
    @SerialName("wind_gusts_10m") val windGusts: Double,
    @SerialName("is_day") val isDay: Int = 1,
)

/** Hodinová data jsou sloupcová; konec předpovědi může obsahovat `null`. */
@Serializable
data class HourlyDto(
    val time: List<String>,
    @SerialName("temperature_2m") val temperature: List<Double?>,
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m") val humidity: List<Int?> = emptyList(),
    @SerialName("dew_point_2m") val dewPoint: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("cloud_cover") val cloudCover: List<Int?> = emptyList(),
    @SerialName("pressure_msl") val pressure: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Int?> = emptyList(),
    @SerialName("wind_gusts_10m") val windGusts: List<Double?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
)

/** Denní agregace (dnešek + další dny); pravděpodobnost srážek nemusí model vracet. */
@Serializable
data class DailyDto(
    val time: List<String>,
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    @SerialName("precipitation_sum") val precipitationSum: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max") val windSpeedMax: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m_max") val windGustsMax: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m_dominant") val windDirectionDominant: List<Int?> = emptyList(),
)

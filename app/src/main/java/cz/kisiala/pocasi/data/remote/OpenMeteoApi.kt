package cz.kisiala.pocasi.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo forecast API s modelem ČHMÚ ALADIN.
 * Dokumentace: https://open-meteo.com/en/docs/chmi-api
 * Výchozí jednotky odpovídají potřebě aplikace: °C, km/h, mm, hPa.
 */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("models") models: String = MODEL,
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = FORECAST_DAYS,
        @Query("current", encoded = true) current: String = VARIABLES,
        @Query("hourly", encoded = true) hourly: String = VARIABLES,
        @Query("daily", encoded = true) daily: String = DAILY_VARIABLES,
        /** Srážky po 15 min (jen pro upozornění – rada k deštníku na příští hodinu); `null` = nestahovat. */
        @Query("minutely_15") minutely15: String? = null,
        @Query("forecast_minutely_15") forecastMinutely15: Int? = null,
    ): ForecastResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val MODEL = "chmi_aladin_seamless"

        /** Dnešek + 5 dní výhledu – jeden dotaz pro obrazovky Dnes i Výhled. */
        const val FORECAST_DAYS = 6

        private val DAILY_VARIABLES = listOf(
            "temperature_2m_max",
            "temperature_2m_min",
            "precipitation_sum",
            "precipitation_probability_max",
            "wind_speed_10m_max",
            "wind_gusts_10m_max",
            "wind_direction_10m_dominant",
        ).joinToString(",")

        private val VARIABLES = listOf(
            "temperature_2m",
            "apparent_temperature",
            "relative_humidity_2m",
            "dew_point_2m",
            "precipitation",
            "cloud_cover",
            "pressure_msl",
            "wind_speed_10m",
            "wind_direction_10m",
            "wind_gusts_10m",
            "is_day",
        ).joinToString(",")
    }
}

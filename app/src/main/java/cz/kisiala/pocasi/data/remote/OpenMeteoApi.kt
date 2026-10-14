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
        @Query("forecast_days") forecastDays: Int = 3,
        @Query("current", encoded = true) current: String = VARIABLES,
        @Query("hourly", encoded = true) hourly: String = VARIABLES,
    ): ForecastResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
        const val MODEL = "chmi_aladin_seamless"

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

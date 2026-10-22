package cz.kisiala.pocasi.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo Geocoding API – vyhledávání míst podle názvu (GeoNames), bez API klíče.
 * Dokumentace: https://open-meteo.com/en/docs/geocoding-api
 */
interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 30,
        @Query("language") language: String = "cs",
        @Query("format") format: String = "json",
    ): GeocodingResponse

    companion object {
        const val BASE_URL = "https://geocoding-api.open-meteo.com/"
    }
}

@Serializable
data class GeocodingResponse(
    /** Bez shody API pole `results` vůbec nevrací. */
    val results: List<GeocodingResult> = emptyList(),
)

@Serializable
data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("country_code") val countryCode: String? = null,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
)

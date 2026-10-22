package cz.kisiala.pocasi

import android.content.Context
import cz.kisiala.pocasi.data.PlaceRepository
import cz.kisiala.pocasi.data.PlaceSearchRepository
import cz.kisiala.pocasi.data.SettingsRepository
import cz.kisiala.pocasi.data.WeatherRepository
import cz.kisiala.pocasi.data.location.LocationProvider
import cz.kisiala.pocasi.data.radar.RadarRepository
import cz.kisiala.pocasi.data.remote.GeocodingApi
import cz.kisiala.pocasi.data.remote.OpenMeteoApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Ruční DI – jediná instance pro celou aplikaci (viz [PocasiApp]). */
class AppContainer(context: Context) {

    /** Scope pro práci, která má přežít jednotlivé obrazovky (např. zjištění polohy). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val openMeteoApi: OpenMeteoApi = Retrofit.Builder()
        .baseUrl(OpenMeteoApi.BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenMeteoApi::class.java)

    private val geocodingApi: GeocodingApi = Retrofit.Builder()
        .baseUrl(GeocodingApi.BASE_URL)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(GeocodingApi::class.java)

    val placeSearchRepository = PlaceSearchRepository(geocodingApi)

    val settingsRepository = SettingsRepository(context)

    val locationProvider = LocationProvider(context)

    val weatherRepository = WeatherRepository(openMeteoApi)

    val radarRepository = RadarRepository(okHttp)

    val placeRepository = PlaceRepository(
        locationProvider = locationProvider,
        scope = appScope,
        myLocationLabel = context.getString(R.string.place_my_location),
    )
}

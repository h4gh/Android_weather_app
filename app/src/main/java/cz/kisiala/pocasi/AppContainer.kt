package cz.kisiala.pocasi

import android.content.Context
import cz.kisiala.pocasi.data.AppLocales
import cz.kisiala.pocasi.data.PlaceRepository
import cz.kisiala.pocasi.data.PlaceSearchRepository
import cz.kisiala.pocasi.data.PlaceStore
import cz.kisiala.pocasi.data.SettingsRepository
import cz.kisiala.pocasi.data.WeatherRepository
import cz.kisiala.pocasi.data.location.LocationProvider
import cz.kisiala.pocasi.data.radar.RadarRepository
import cz.kisiala.pocasi.data.remote.GeocodingApi
import cz.kisiala.pocasi.data.remote.OpenMeteoApi
import cz.kisiala.pocasi.notifications.NotificationScheduler
import cz.kisiala.pocasi.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    val placeSearchRepository = PlaceSearchRepository(geocodingApi) { AppLocales.contentLanguage(context) }

    val settingsRepository = SettingsRepository(context)

    val locationProvider = LocationProvider(context)

    val weatherRepository = WeatherRepository(openMeteoApi)

    val radarRepository = RadarRepository(okHttp)

    val placeRepository = PlaceRepository(
        locationProvider = locationProvider,
        store = PlaceStore(context),
        scope = appScope,
        myLocationLabel = { context.getString(R.string.place_my_location) },
    )

    init {
        val appContext = context.applicationContext
        // Widgety s „Moje poloha“ berou polohu z aplikace – po jejím novém zjištění je obnovíme.
        appScope.launch {
            placeRepository.lastLocation
                .map { it?.let { place -> place.latitude to place.longitude } }
                .distinctUntilChanged()
                .drop(1)
                .collect { WidgetUpdater.refreshAll(appContext, onlyCurrentLocation = true) }
        }
        // Každá změna časů upozornění (i první načtení po startu) přeplánuje budík.
        appScope.launch {
            settingsRepository.notifications.distinctUntilChanged().collect { NotificationScheduler.reschedule(appContext) }
        }
        // Změna vzhledu v aplikaci platí i pro widgety.
        appScope.launch {
            settingsRepository.themeMode.distinctUntilChanged().drop(1).collect { WidgetUpdater.redrawAll(appContext) }
        }
    }
}

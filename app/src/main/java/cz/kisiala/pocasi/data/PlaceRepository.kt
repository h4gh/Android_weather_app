package cz.kisiala.pocasi.data

import cz.kisiala.pocasi.data.location.LocationProvider
import cz.kisiala.pocasi.model.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zdroj pravdy o místech: aktuální poloha + uložená místa a to, které je vybrané.
 *
 * Oblíbená místa: stačí nahradit [savedPlaces] tokem z perzistentního úložiště
 * (DataStore/Room) a doplnit metody pro přidání/odebrání – UI už pracuje se seznamem [places].
 */
class PlaceRepository(
    private val locationProvider: LocationProvider,
    scope: CoroutineScope,
    private val myLocationLabel: String,
) {
    private val currentLocationPlace = MutableStateFlow<Place?>(null)
    private val savedPlaces = MutableStateFlow(listOf(Place.Prague))
    private val selectedId = MutableStateFlow<String?>(null)
    private val locationResolved = MutableStateFlow(false)
    private val mutex = Mutex()

    /** Místa k výběru v top app baru – aktuální poloha (je-li známa) a pak uložená místa. */
    val places: StateFlow<List<Place>> =
        combine(currentLocationPlace, savedPlaces) { current, saved -> listOfNotNull(current) + saved }
            .stateIn(scope, SharingStarted.Eagerly, savedPlaces.value)

    /** Vybrané místo; `null`, dokud nevíme, zda máme polohu (čeká se na oprávnění/GPS). */
    val selectedPlace: StateFlow<Place?> =
        combine(places, selectedId, locationResolved) { list, id, resolved ->
            if (!resolved && id == null) null else list.firstOrNull { it.id == id } ?: list.first()
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Poloha zařízení (je-li známa) – pro tečku „Moje poloha“ na mapě radaru. */
    val currentLocation: StateFlow<Place?> = currentLocationPlace.asStateFlow()

    val isLocationResolved: Boolean get() = locationResolved.value

    fun select(place: Place) {
        selectedId.value = place.id
    }

    /**
     * Zjistí aktuální polohu (pokud je oprávnění) a její název.
     * Bez polohy zůstane k dispozici jen výchozí místo (Praha).
     */
    suspend fun refreshCurrentLocation() = mutex.withLock {
        val location = runCatching { locationProvider.currentLocation() }.getOrNull()
        if (location == null) {
            currentLocationPlace.value = null
            locationResolved.value = true
            return@withLock
        }
        // Nejdřív souřadnice (spustí načtení předpovědi), název doplníme, až ho geokodér vrátí.
        val previousName = currentLocationPlace.value?.name
        currentLocationPlace.value = Place(
            id = Place.CURRENT_LOCATION_ID,
            name = previousName ?: myLocationLabel,
            latitude = location.latitude,
            longitude = location.longitude,
            kind = Place.Kind.CurrentLocation,
        )
        locationResolved.value = true
        val name = runCatching { locationProvider.placeName(location.latitude, location.longitude) }.getOrNull()
        currentLocationPlace.value = currentLocationPlace.value?.copy(name = name ?: myLocationLabel)
    }
}

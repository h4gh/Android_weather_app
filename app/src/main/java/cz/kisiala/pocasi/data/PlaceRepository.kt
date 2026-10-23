package cz.kisiala.pocasi.data

import cz.kisiala.pocasi.data.location.LocationProvider
import cz.kisiala.pocasi.model.Place
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Zdroj pravdy o místech: aktuální poloha, oblíbená místa (trvale v [PlaceStore]), místo z vyhledávání
 * a to, které je vybrané (pamatuje se i po restartu).
 */
class PlaceRepository(
    private val locationProvider: LocationProvider,
    private val store: PlaceStore,
    private val scope: CoroutineScope,
    private val myLocationLabel: () -> String,
) {
    private val currentLocationPlace = MutableStateFlow<Place?>(null)
    /** Vybrané místo z vyhledávání, které není v oblíbených (v menu míst, dokud je vybrané). */
    private val searchedPlace = MutableStateFlow<Place?>(null)
    private val savedPlaces = MutableStateFlow(emptyList<Place>())
    private val selectedId = MutableStateFlow<String?>(null)
    private val locationResolved = MutableStateFlow(false)
    /** Oblíbená i naposledy vybrané místo jsou načtené z úložiště. */
    private val restored = MutableStateFlow(false)
    private val mutex = Mutex()

    init {
        scope.launch {
            savedPlaces.value = store.favorites.first()
            store.lastSelected.first()?.let { last ->
                // Vybrané místo mimo oblíbené (hledané, případně mezitím odebrané) obnovíme jako hledané.
                if (!last.isCurrentLocation && last.kind != Place.Kind.Default && savedPlaces.value.none { it.id == last.id }) {
                    searchedPlace.value = last.copy(kind = Place.Kind.Search)
                }
                selectedId.value = last.id
            }
            restored.value = true
        }
    }

    /**
     * Místa k výběru v top app baru – aktuální poloha, oblíbená a hledané místo.
     * Záložní Frýdek-Místek jen tehdy, když chybí poloha i oblíbená místa.
     */
    val places: StateFlow<List<Place>> =
        combine(currentLocationPlace, searchedPlace, savedPlaces) { current, searched, saved ->
            val fallback = if (current == null && saved.isEmpty()) listOf(Place.FrydekMistek) else emptyList()
            (listOfNotNull(current) + saved + listOfNotNull(searched) + fallback).distinctBy { it.id }
        }.stateIn(scope, SharingStarted.Eagerly, listOf(Place.FrydekMistek))

    /** Oblíbená místa v pořadí, jak si je uživatel seřadil. */
    val favorites: StateFlow<List<Place>> = savedPlaces.asStateFlow()

    /**
     * Vybrané místo; `null`, dokud se nenačte úložiště a dokud nevíme, zda máme polohu
     * (čeká se na oprávnění/GPS) – pokud se má zobrazit právě poloha.
     */
    val selectedPlace: StateFlow<Place?> =
        combine(places, selectedId, locationResolved, restored) { list, id, resolved, ready ->
            when {
                !ready -> null
                !resolved && (id == null || id == Place.CURRENT_LOCATION_ID) -> null
                else -> list.firstOrNull { it.id == id } ?: list.first()
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Poslední uložená poloha zařízení (přežije restart) – zdroj pro widgety s „Moje poloha“. */
    val lastLocation: Flow<Place?> = store.lastLocation

    /** Poloha zařízení (je-li známa) – pro tečku „Moje poloha“ na mapě radaru. */
    val currentLocation: StateFlow<Place?> = currentLocationPlace.asStateFlow()

    val isLocationResolved: Boolean get() = locationResolved.value

    fun select(place: Place) {
        // Hledané místo mimo oblíbené je v menu jen, dokud je vybrané – přepnutím jinam zmizí
        // (jinak by v menu zůstalo např. místo právě odebrané z oblíbených a nešlo by ho smazat).
        searchedPlace.value = place.takeIf { it.kind == Place.Kind.Search && !isFavorite(it) }
        selectedId.value = place.id
        scope.launch { store.setLastSelected(places.value.firstOrNull { it.id == place.id } ?: place) }
    }

    fun isFavorite(place: Place): Boolean = savedPlaces.value.any { it.id == place.id }

    /** Přidá místo na konec oblíbených (aktuální polohu ne – ta se mění). */
    fun addFavorite(place: Place) {
        if (place.isCurrentLocation || isFavorite(place)) return
        val saved = place.copy(kind = Place.Kind.Saved)
        if (searchedPlace.value?.id == place.id) searchedPlace.value = null
        updateFavorites(savedPlaces.value + saved)
    }

    fun removeFavorite(place: Place) {
        // Právě zobrazené místo po odebrání zůstane vybrané jako hledané, ať se předpověď nepřepne jinam.
        if (selectedId.value == place.id) searchedPlace.value = place.copy(kind = Place.Kind.Search)
        updateFavorites(savedPlaces.value.filterNot { it.id == place.id })
    }

    fun toggleFavorite(place: Place) = if (isFavorite(place)) removeFavorite(place) else addFavorite(place)

    /** Nové pořadí oblíbených (úprava v seznamu oblíbených, i vrácení odebraného místa). */
    fun setFavorites(places: List<Place>) {
        if (searchedPlace.value?.let { s -> places.any { it.id == s.id } } == true) searchedPlace.value = null
        updateFavorites(places.map { it.copy(kind = Place.Kind.Saved) })
    }

    private fun updateFavorites(places: List<Place>) {
        savedPlaces.value = places
        scope.launch {
            store.setFavorites(places)
        }
    }

    /**
     * Zjistí aktuální polohu (pokud je oprávnění) a její název.
     * Bez polohy zůstane k dispozici záložní místo (Frýdek-Místek).
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
            name = previousName ?: myLocationLabel(),
            latitude = location.latitude,
            longitude = location.longitude,
            kind = Place.Kind.CurrentLocation,
        )
        locationResolved.value = true
        val name = runCatching { locationProvider.placeName(location.latitude, location.longitude) }.getOrNull()
        currentLocationPlace.value = currentLocationPlace.value?.copy(name = name ?: myLocationLabel())
        // Pro widgety s „Moje poloha“ – ty polohu na pozadí nezjišťují (bez oprávnění „Povolit vždy“).
        currentLocationPlace.value?.let { store.setLastLocation(it) }
    }
}

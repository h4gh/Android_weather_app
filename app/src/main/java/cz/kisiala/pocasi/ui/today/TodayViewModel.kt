package cz.kisiala.pocasi.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.kisiala.pocasi.PocasiApp
import cz.kisiala.pocasi.data.PlaceRepository
import cz.kisiala.pocasi.data.WeatherRepository
import cz.kisiala.pocasi.model.Forecast
import cz.kisiala.pocasi.model.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class TodayUiState(
    val place: Place? = null,
    val places: List<Place> = emptyList(),
    val forecast: Forecast? = null,
    /** Načítání bez dat k zobrazení (první načtení, změna místa). */
    val isLoading: Boolean = true,
    /** Pull-to-refresh nad již zobrazenými daty. */
    val isRefreshing: Boolean = false,
    val error: LoadError? = null,
)

enum class LoadError { Network, Other }

class TodayViewModel(
    private val placeRepository: PlaceRepository,
    private val weatherRepository: WeatherRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    /** Každá změna spustí nové načtení pro aktuální místo. */
    private val reloadTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            placeRepository.places.collect { places -> _state.update { it.copy(places = places) } }
        }
        viewModelScope.launch {
            // Změna jen názvu (doplněný z geokodéru) nevyžaduje nové načtení.
            placeRepository.selectedPlace.filterNotNull().collect { place ->
                _state.update { if (it.place?.id == place.id) it.copy(place = place) else it }
            }
        }
        viewModelScope.launch {
            val placeForLoad = placeRepository.selectedPlace.filterNotNull()
                .distinctUntilChanged { a, b -> a.id == b.id && a.latitude == b.latitude && a.longitude == b.longitude }
            combine(placeForLoad, reloadTick) { place, _ -> place }
                .collectLatest { load(placeRepository.selectedPlace.value ?: it) }
        }
    }

    private suspend fun load(place: Place) {
        _state.update {
            val samePlace = it.place?.id == place.id
            it.copy(
                place = place,
                forecast = if (samePlace) it.forecast else null,
                isLoading = !samePlace || it.forecast == null,
                error = null,
            )
        }
        try {
            val forecast = weatherRepository.forecast(place)
            _state.update { it.copy(forecast = forecast, isLoading = false, isRefreshing = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = if (e is IOException) LoadError.Network else LoadError.Other
            _state.update { it.copy(error = error, isLoading = false, isRefreshing = false) }
        }
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = it.forecast != null) }
        viewModelScope.launch {
            // U aktuální polohy zároveň zpřesníme polohu (uživatel se mohl přesunout).
            if (_state.value.place?.isCurrentLocation == true) placeRepository.refreshCurrentLocation()
            reloadTick.update { it + 1 }
        }
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        reloadTick.update { it + 1 }
    }

    fun selectPlace(place: Place) = placeRepository.select(place)

    fun errorShown() = _state.update { it.copy(error = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PocasiApp
                TodayViewModel(app.container.placeRepository, app.container.weatherRepository)
            }
        }
    }
}

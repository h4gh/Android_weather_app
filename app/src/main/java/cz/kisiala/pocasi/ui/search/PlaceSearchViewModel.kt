package cz.kisiala.pocasi.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.kisiala.pocasi.PocasiApp
import cz.kisiala.pocasi.data.PlaceRepository
import cz.kisiala.pocasi.data.PlaceSearchRepository
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.ui.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class PlaceSearchUiState(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val isSearching: Boolean = false,
    val error: LoadError? = null,
    /** Id oblíbených míst – hvězdička u výsledků. */
    val favoriteIds: Set<String> = emptySet(),
) {
    /** Dotaz je dost dlouhý na hledání a výsledky k němu už dorazily. */
    val showsNoResults: Boolean
        get() = query.trim().length >= MIN_QUERY_LENGTH && !isSearching && error == null && results.isEmpty()

    companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

class PlaceSearchViewModel(
    private val searchRepository: PlaceSearchRepository,
    private val placeRepository: PlaceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaceSearchUiState())
    val state: StateFlow<PlaceSearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.map { it.query.trim() }.distinctUntilChanged().collectLatest { search(it) }
        }
        viewModelScope.launch {
            placeRepository.favorites.collect { list -> _state.update { it.copy(favoriteIds = list.mapTo(HashSet()) { p -> p.id }) } }
        }
    }

    private suspend fun search(query: String) {
        if (query.length < PlaceSearchUiState.MIN_QUERY_LENGTH) {
            _state.update { it.copy(results = emptyList(), isSearching = false, error = null) }
            return
        }
        _state.update { it.copy(isSearching = true, error = null) }
        // Počkáme, až uživatel dopíše (collectLatest hledání při dalším znaku zruší).
        delay(DEBOUNCE_MS)
        try {
            val results = searchRepository.search(query)
            _state.update { it.copy(results = results, isSearching = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = if (e is IOException) LoadError.Network else LoadError.Other
            _state.update { it.copy(results = emptyList(), isSearching = false, error = error) }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun retry() {
        viewModelScope.launch { search(_state.value.query.trim()) }
    }

    fun select(place: Place) = placeRepository.select(place)

    fun toggleFavorite(place: Place) = placeRepository.toggleFavorite(place)

    companion object {
        private const val DEBOUNCE_MS = 300L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PocasiApp
                PlaceSearchViewModel(app.container.placeSearchRepository, app.container.placeRepository)
            }
        }
    }
}

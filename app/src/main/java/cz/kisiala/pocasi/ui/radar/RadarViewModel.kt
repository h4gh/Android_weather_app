package cz.kisiala.pocasi.ui.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cz.kisiala.pocasi.PocasiApp
import cz.kisiala.pocasi.data.PlaceRepository
import cz.kisiala.pocasi.data.location.LocationProvider
import cz.kisiala.pocasi.data.radar.RadarFrame
import cz.kisiala.pocasi.data.radar.RadarRepository
import cz.kisiala.pocasi.model.NearbyPrecipitation
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.ui.LoadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Duration
import java.time.Instant

data class RadarUiState(
    /** Snímky od nejstaršího po nejnovější; dokud se nenačte animace, jen ten nejnovější. */
    val frames: List<RadarFrame> = emptyList(),
    /** Index zobrazeného snímku ve [frames]. */
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val error: LoadError? = null,
    /** Nejbližší srážky od vybraného místa podle nejnovějšího snímku; `null` = zatím nevíme. */
    val nearby: NearbyPrecipitation? = null,
    val myLocation: Place? = null,
    val translucent: Boolean = false,
) {
    val latest: RadarFrame? get() = frames.lastOrNull()
    val displayed: RadarFrame? get() = frames.getOrNull(index)
}

/**
 * Radar ČHMÚ – sdílený obrazovkami Dnes (karta „Nejbližší srážky“) a Radar (animace).
 * Nejnovější snímek se načte hned, starší snímky pro animaci až při otevření Radaru.
 */
class RadarViewModel(
    private val radarRepository: RadarRepository,
    private val placeRepository: PlaceRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(RadarUiState())
    val state: StateFlow<RadarUiState> = _state.asStateFlow()

    private val _locateResult = Channel<Place?>(Channel.CONFLATED)

    /** Výsledek tlačítka „Moje poloha“ – místo k vycentrování, nebo `null`, když poloha není k dispozici. */
    val locateResult: Flow<Place?> = _locateResult.receiveAsFlow()

    private var lastCheck: Instant = Instant.MIN
    private var animationWanted = false
    private var loadJob: Job? = null
    private var playJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            placeRepository.currentLocation.collect { place -> _state.update { it.copy(myLocation = place) } }
        }
        viewModelScope.launch {
            val latest = _state.map { it.latest }.distinctUntilChanged()
            combine(latest, placeRepository.selectedPlace) { frame, place -> frame to place }
                .collectLatest { (frame, place) ->
                    val nearby = if (frame != null && place != null) {
                        withContext(Dispatchers.Default) {
                            NearbyPrecipitation.find(frame.levels, place.latitude, place.longitude)
                        }
                    } else null
                    _state.update { it.copy(nearby = nearby) }
                }
        }
    }

    /** Nový snímek vzniká každých 5 min – starší data obnovíme (návrat do aplikace, otevření Radaru). */
    fun refreshIfStale() {
        if (loadJob?.isActive != true && Duration.between(lastCheck, Instant.now()) > STALE_AFTER) load()
    }

    fun retry() {
        _state.update { it.copy(isLoading = true, error = null) }
        load()
    }

    /** Volá obrazovka Radar – teprve teď stahujeme snímky pro animaci. */
    fun onRadarShown() {
        animationWanted = true
        val latest = _state.value.latest
        if (latest != null && _state.value.frames.size == 1) loadAnimation(latest) else refreshIfStale()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.frames.isEmpty(), error = null) }
            try {
                val latest = radarRepository.latestFrame()
                lastCheck = Instant.now()
                if (_state.value.latest?.time != latest.time) {
                    stopPlayback()
                    // Okno animace se posune; staré snímky necháme, dokud nepřijdou nové.
                    _state.update { state ->
                        val frames = state.frames.filter { it.time < latest.time } + latest
                        state.copy(frames = frames, index = frames.lastIndex)
                    }
                }
                _state.update { it.copy(isLoading = false) }
                if (animationWanted) fetchAnimation(latest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = if (e is IOException) LoadError.Network else LoadError.Other
                _state.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    private suspend fun fetchAnimation(latest: RadarFrame) {
        val frames = runCatching { radarRepository.animationFrames(latest) }.getOrNull() ?: return
        _state.update { state ->
            if (state.latest?.time != latest.time) return@update state
            val atLatest = state.index == state.frames.lastIndex
            val shownTime = state.displayed?.time
            val index = if (atLatest) frames.lastIndex else frames.indexOfFirst { it.time == shownTime }
            state.copy(frames = frames, index = index.takeIf { it >= 0 } ?: frames.lastIndex)
        }
    }

    private fun loadAnimation(latest: RadarFrame) {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch { fetchAnimation(latest) }
    }

    fun selectFrame(index: Int) {
        stopPlayback()
        _state.update { it.copy(index = index.coerceIn(0, (it.frames.size - 1).coerceAtLeast(0))) }
    }

    fun togglePlayback() {
        if (playJob?.isActive == true) {
            stopPlayback()
            return
        }
        if (_state.value.frames.size < 2) return
        _state.update {
            it.copy(isPlaying = true, index = if (it.index == it.frames.lastIndex) 0 else it.index)
        }
        playJob = viewModelScope.launch {
            while (true) {
                val atLatest = _state.value.index == _state.value.frames.lastIndex
                delay(if (atLatest) LAST_FRAME_PAUSE_MS else FRAME_MS)
                _state.update { it.copy(index = if (atLatest) 0 else it.index + 1) }
            }
        }
    }

    private fun stopPlayback() {
        playJob?.cancel()
        playJob = null
        _state.update { it.copy(isPlaying = false) }
    }

    fun toggleTranslucent() = _state.update { it.copy(translucent = !it.translucent) }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    /** Zjistí aktuální polohu (aktualizuje i místo „Moje poloha“) a pošle ji k vycentrování mapy. */
    fun locateMe() {
        viewModelScope.launch {
            placeRepository.refreshCurrentLocation()
            _locateResult.send(placeRepository.currentLocation.value)
        }
    }

    companion object {
        private val STALE_AFTER = Duration.ofMinutes(4)
        private const val FRAME_MS = 500L
        private const val LAST_FRAME_PAUSE_MS = 1_500L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as PocasiApp
                RadarViewModel(app.container.radarRepository, app.container.placeRepository, app.container.locationProvider)
            }
        }
    }
}

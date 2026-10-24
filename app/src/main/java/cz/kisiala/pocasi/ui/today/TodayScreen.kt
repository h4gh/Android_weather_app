package cz.kisiala.pocasi.ui.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.kisiala.pocasi.ui.theme.WeatherTheme
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.Forecast
import cz.kisiala.pocasi.model.NearbyPrecipitation
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.ui.ForecastViewModel
import cz.kisiala.pocasi.ui.common.AppMenuActions
import cz.kisiala.pocasi.ui.common.AppOverflowMenu
import cz.kisiala.pocasi.ui.common.ErrorState
import cz.kisiala.pocasi.ui.common.LoadingState
import cz.kisiala.pocasi.ui.common.PlacesMenu
import cz.kisiala.pocasi.ui.common.message
import cz.kisiala.pocasi.ui.radar.RadarUiState
import cz.kisiala.pocasi.ui.radar.RadarViewModel
import cz.kisiala.pocasi.ui.radar.arrowRotation
import cz.kisiala.pocasi.ui.radar.directionAdverb
import cz.kisiala.pocasi.ui.radar.formatRadarTime
import cz.kisiala.pocasi.ui.radar.label
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenRadar: () -> Unit,
    menuActions: AppMenuActions,
    modifier: Modifier = Modifier,
    viewModel: ForecastViewModel = viewModel(factory = ForecastViewModel.Factory),
    radarViewModel: RadarViewModel = viewModel(factory = RadarViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val radarState by radarViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val errorText = state.error?.let { stringResource(it.message) }

    // Chyba při obnově nad zobrazenými daty → jen snackbar, data zůstávají.
    LaunchedEffect(state.error, state.forecast != null) {
        if (errorText != null && state.forecast != null) {
            snackbarHostState.showSnackbar(errorText)
            viewModel.errorShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TodayTopBar(
                place = state.place,
                places = state.places,
                onSelectPlace = viewModel::selectPlace,
                onSearch = menuActions.onSearchPlace,
                onToggleFavorite = { place ->
                    val message = context.getString(
                        if (place.kind == Place.Kind.Saved) R.string.favorite_removed else R.string.favorite_added,
                        place.name,
                    )
                    viewModel.toggleFavorite(place)
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                },
                menuActions = menuActions,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val forecast = state.forecast
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                forecast != null -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = {
                        viewModel.refresh()
                        radarViewModel.refreshIfStale()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TodayContent(forecast, radarState, onOpenRadar)
                }
                state.error != null && !state.isLoading -> ErrorState(state.error!!, onRetry = viewModel::retry)
                else -> LoadingState()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(
    place: Place?,
    places: List<Place>,
    onSelectPlace: (Place) -> Unit,
    onSearch: () -> Unit,
    onToggleFavorite: (Place) -> Unit,
    menuActions: AppMenuActions,
) {
    var placesOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (place != null) Box {
                TextButton(
                    onClick = { placesOpen = true },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        place.name,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.action_choose_place),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                PlacesMenu(
                    expanded = placesOpen,
                    selected = place,
                    places = places,
                    onSelect = {
                        placesOpen = false
                        onSelectPlace(it)
                    },
                    onDismiss = { placesOpen = false },
                    actions = menuActions,
                )
            }
        },
        actions = {
            // Aktuální poloha se mění – do oblíbených se ukládají jen konkrétní místa.
            if (place != null && !place.isCurrentLocation) {
                val favorite = place.kind == Place.Kind.Saved
                IconButton(onClick = { onToggleFavorite(place) }) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(
                            if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                        ),
                        tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
            }
            AppOverflowMenu(menuActions)
        },
    )
}

@Composable
private fun TodayContent(forecast: Forecast, radar: RadarUiState, onOpenRadar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CurrentConditions(forecast)
        NearestPrecipitationCard(radar, onOpenRadar)
        Next24HoursCard(forecast.next(24))
        DetailCards(forecast)
    }
}

@Composable
private fun CurrentConditions(forecast: Forecast) {
    val current = forecast.current
    val condition = stringResource(current.condition.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                formatTemperature(current.temperature),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = MaterialTheme.typography.displayLarge.fontSize * 1.4f,
                    lineHeight = MaterialTheme.typography.displayLarge.lineHeight * 1.4f,
                ),
            )
            Text(
                condition,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.feels_like_line,
                    formatTemperature(current.apparentTemperature),
                    formatTemperature(forecast.todayMax),
                    formatTemperature(forecast.todayMin),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WeatherIcon(
            type = current.condition.icon,
            isDay = current.isDay,
            contentDescription = condition,
            modifier = Modifier.size(80.dp),
        )
    }
}

/** Nejbližší srážky z posledního snímku radaru ČHMÚ (do 100 km od místa). */
@Composable
private fun NearestPrecipitationCard(radar: RadarUiState, onOpenRadar: () -> Unit) {
    val latest = radar.latest
    val nearby = radar.nearby
    val time = latest?.let { formatRadarTime(it.time) }
    val title: String
    val detail: String
    when {
        latest != null && nearby is NearbyPrecipitation.Found -> {
            title = if (nearby.distanceKm == 0) {
                stringResource(R.string.precip_here)
            } else {
                stringResource(R.string.precip_found, nearby.distanceKm, directionAdverb(nearby.bearing))
            }
            detail = stringResource(R.string.precip_detail, time!!, stringResource(nearby.intensity.label))
        }
        latest != null && nearby == NearbyPrecipitation.None -> {
            title = stringResource(R.string.precip_none)
            detail = stringResource(
                R.string.precip_none_detail, time!!, NearbyPrecipitation.SEARCH_RADIUS_KM.toInt(),
            )
        }
        latest != null && nearby == NearbyPrecipitation.OutOfCoverage -> {
            title = stringResource(R.string.precip_out_of_coverage)
            detail = stringResource(R.string.precip_out_of_coverage_detail)
        }
        radar.error != null && !radar.isLoading -> {
            title = stringResource(R.string.precip_title)
            detail = stringResource(R.string.precip_error)
        }
        else -> {
            title = stringResource(R.string.precip_title)
            detail = stringResource(R.string.precip_loading)
        }
    }
    WeatherCard {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val tint = MaterialTheme.colorScheme.onPrimaryContainer
                when {
                    latest == null && radar.error == null ->
                        CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp)
                    nearby is NearbyPrecipitation.Found && nearby.distanceKm > 0 -> Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.rotate(arrowRotation(nearby.bearing)),
                    )
                    nearby is NearbyPrecipitation.Found -> Icon(Icons.Outlined.Umbrella, null, tint = tint)
                    latest == null -> Icon(Icons.Outlined.CloudOff, null, tint = tint)
                    else -> Icon(Icons.Outlined.Radar, null, tint = tint)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenRadar) {
                Text(stringResource(R.string.action_map), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun DetailCards(forecast: Forecast) {
    val c = forecast.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailCard(
            title = stringResource(R.string.card_wind),
            value = c.windSpeed.roundToInt().toString(),
            unit = stringResource(R.string.unit_kmh),
            detail = stringResource(R.string.wind_detail, windDirectionName(c.windDirection), c.windGusts.roundToInt()),
            modifier = Modifier.weight(1f),
        )
        DetailCard(
            title = stringResource(R.string.card_humidity),
            value = c.humidity.toString(),
            unit = stringResource(R.string.unit_percent),
            detail = stringResource(R.string.dew_point, formatTemperature(c.dewPoint)),
            modifier = Modifier.weight(1f),
        )
        DetailCard(
            title = stringResource(R.string.card_pressure),
            value = c.pressure.roundToInt().toString(),
            unit = null,
            detail = stringResource(R.string.pressure_detail, stringResource(forecast.pressureTrend.label)),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DetailCard(title: String, value: String, unit: String?, detail: String, modifier: Modifier = Modifier) {
    WeatherCard(modifier.fillMaxHeight()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildAnnotatedString {
                    append(value)
                    if (unit != null) {
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodyLarge.fontSize)) {
                            append(" ")
                            append(unit)
                        }
                    }
                },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tónovaná karta podle návrhu: surface container, rohy 12 dp. */
@Composable
fun WeatherCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = if (WeatherTheme.blackBackground) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        content = content,
    )
}

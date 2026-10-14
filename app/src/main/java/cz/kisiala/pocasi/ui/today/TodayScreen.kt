package cz.kisiala.pocasi.ui.today

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.kisiala.pocasi.BuildConfig
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.Forecast
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.ui.ForecastViewModel
import cz.kisiala.pocasi.ui.common.ErrorState
import cz.kisiala.pocasi.ui.common.LoadingState
import cz.kisiala.pocasi.ui.common.PlacesMenu
import cz.kisiala.pocasi.ui.common.message
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onOpenRadar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ForecastViewModel = viewModel(factory = ForecastViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAbout by rememberSaveable { mutableStateOf(false) }

    val searchSoon = stringResource(R.string.search_coming_soon)
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
                onSearch = { scope.launch { snackbarHostState.showSnackbar(searchSoon) } },
                onAbout = { showAbout = true },
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
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TodayContent(forecast, onOpenRadar)
                }
                state.error != null && !state.isLoading -> ErrorState(state.error!!, onRetry = viewModel::retry)
                else -> LoadingState()
            }
        }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayTopBar(
    place: Place?,
    places: List<Place>,
    onSelectPlace: (Place) -> Unit,
    onSearch: () -> Unit,
    onAbout: () -> Unit,
) {
    var placesOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

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
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_about)) },
                        onClick = {
                            menuOpen = false
                            onAbout()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun TodayContent(forecast: Forecast, onOpenRadar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CurrentConditions(forecast)
        NearestPrecipitationCard(onOpenRadar)
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

/** Placeholder – skutečná data z radaru ČHMÚ přibudou v kroku 3. */
@Composable
private fun NearestPrecipitationCard(onOpenRadar: () -> Unit) {
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
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(stringResource(R.string.precip_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.precip_placeholder),
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
        content = content,
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_about)) },
        text = {
            Column {
                Text(stringResource(R.string.about_text))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

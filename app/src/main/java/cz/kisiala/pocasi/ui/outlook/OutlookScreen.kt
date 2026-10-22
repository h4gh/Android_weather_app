package cz.kisiala.pocasi.ui.outlook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.East
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.DailyWeather
import cz.kisiala.pocasi.model.HourlyWeather
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.ui.ForecastViewModel
import cz.kisiala.pocasi.ui.common.AppMenuActions
import cz.kisiala.pocasi.ui.common.AppOverflowMenu
import cz.kisiala.pocasi.ui.common.ErrorState
import cz.kisiala.pocasi.ui.common.LoadingState
import cz.kisiala.pocasi.ui.common.PlacesMenu
import cz.kisiala.pocasi.ui.common.message
import cz.kisiala.pocasi.ui.theme.WeatherTheme
import cz.kisiala.pocasi.ui.today.Meteogram
import cz.kisiala.pocasi.ui.today.MeteogramLegend
import cz.kisiala.pocasi.ui.today.STRONG_WIND_KMH
import cz.kisiala.pocasi.ui.today.WeatherCard
import cz.kisiala.pocasi.ui.today.WeatherIcon
import cz.kisiala.pocasi.ui.today.formatPrecipitation
import cz.kisiala.pocasi.ui.today.formatTemperature
import cz.kisiala.pocasi.ui.today.label
import cz.kisiala.pocasi.ui.today.windDirectionName
import java.time.LocalDate
import kotlin.math.roundToInt

private const val OUTLOOK_DAYS = 5

/** Prvních N dní výhledu (od zítřka) počítá ALADIN, další dny už jen ECMWF. */
private const val ALADIN_DAYS = 3

/** Hodiny zobrazené v rozbaleném dni (po 3 h, jako v návrhu). */
private val DETAIL_HOURS = listOf(6, 9, 12, 15, 18, 21)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlookScreen(
    viewModel: ForecastViewModel,
    menuActions: AppMenuActions,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
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
            OutlookTopBar(
                place = state.place,
                places = state.places,
                onSelectPlace = viewModel::selectPlace,
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
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OutlookContent(
                        placeName = state.place?.name,
                        days = forecast.outlook(OUTLOOK_DAYS),
                        hoursOf = forecast::hoursOf,
                    )
                }
                state.error != null && !state.isLoading -> ErrorState(state.error!!, onRetry = viewModel::retry)
                else -> LoadingState()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OutlookTopBar(
    place: Place?,
    places: List<Place>,
    onSelectPlace: (Place) -> Unit,
    menuActions: AppMenuActions,
) {
    var placesOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.outlook_title)) },
        actions = {
            if (place != null) Box {
                IconButton(onClick = { placesOpen = true }) {
                    Icon(Icons.Outlined.Place, contentDescription = stringResource(R.string.action_choose_place))
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
            AppOverflowMenu(menuActions)
        },
    )
}

@Composable
private fun OutlookContent(
    placeName: String?,
    days: List<DailyWeather>,
    hoursOf: (LocalDate) -> List<HourlyWeather>,
) {
    // Rozbalený je nejvýš jeden den; výchozí je zítřek.
    var expanded by rememberSaveable { mutableStateOf<Int?>(0) }
    val scaleMin = days.minOfOrNull { it.temperatureMin } ?: 0.0
    val scaleMax = days.maxOfOrNull { it.temperatureMax } ?: 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.outlook_subtitle, placeName.orEmpty()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        )
        // Rozbalený den má vlastní kartu, ostatní dny se sdružují do společných karet.
        // Dny z modelu ECMWF jsou v samostatné skupině s decentním nadpisem.
        dayGroups(days.size, expanded).forEach { group ->
            if (group.first == ALADIN_DAYS) LessCertainLabel()
            WeatherCard {
                group.forEach { index ->
                    if (index != group.first) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    key(days[index].date) {
                        DayItem(
                            day = days[index],
                            hours = hoursOf(days[index].date),
                            isTomorrow = index == 0,
                            expanded = index == expanded,
                            scaleMin = scaleMin,
                            scaleMax = scaleMax,
                            onClick = { expanded = if (expanded == index) null else index },
                        )
                    }
                }
            }
        }
    }
}

/** Rozdělí indexy dní do karet: rozbalený den samostatně a předěl před dny z ECMWF. */
private fun dayGroups(count: Int, expanded: Int?): List<IntRange> {
    val breaks = sortedSetOf(0, count)
    if (expanded != null && expanded < count) {
        breaks += expanded
        breaks += expanded + 1
    }
    if (ALADIN_DAYS < count) breaks += ALADIN_DAYS
    return breaks.zipWithNext { from, to -> from until to }.filter { !it.isEmpty() }
}

@Composable
private fun LessCertainLabel() {
    Text(
        stringResource(R.string.outlook_less_certain),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun DayItem(
    day: DailyWeather,
    hours: List<HourlyWeather>,
    isTomorrow: Boolean,
    expanded: Boolean,
    scaleMin: Double,
    scaleMax: Double,
    onClick: () -> Unit,
) {
    val stateText = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    val actionText = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand)
    Column {
        DayRow(
            day = day,
            isTomorrow = isTomorrow,
            scaleMin = scaleMin,
            scaleMax = scaleMax,
            modifier = Modifier
                .clickable(onClickLabel = actionText, onClick = onClick)
                .semantics(mergeDescendants = true) { stateDescription = stateText },
        )
        if (expanded) {
            val visible = remember { MutableTransitionState(false).apply { targetState = true } }
            AnimatedVisibility(visibleState = visible, enter = expandVertically() + fadeIn()) {
                DayDetail(day, hours)
            }
        }
    }
}

@Composable
private fun DayRow(
    day: DailyWeather,
    isTomorrow: Boolean,
    scaleMin: Double,
    scaleMax: Double,
    modifier: Modifier = Modifier,
) {
    val dayNames = stringArrayResource(R.array.day_short)
    val condition = stringResource(day.condition.label)
    val hasPrecipitation = day.precipitationSum >= 0.05
    val windSpeed = day.windSpeedMax.roundToInt()
    val strongWind = windSpeed >= STRONG_WIND_KMH
    val windColor = if (strongWind) WeatherTheme.colors.temperature else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(56.dp)) {
            Text(
                if (isTomorrow) stringResource(R.string.outlook_tomorrow) else dayNames[day.date.dayOfWeek.value - 1],
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                "${day.date.dayOfMonth}.${day.date.monthValue}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WeatherIcon(
            type = day.condition.icon,
            isDay = true,
            contentDescription = condition,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(32.dp),
        )
        Column(
            Modifier
                .padding(start = 14.dp)
                .width(68.dp),
        ) {
            Text(
                stringResource(R.string.outlook_precip_mm, formatPrecipitation(day.precipitationSum)),
                style = MaterialTheme.typography.titleMedium,
                color = if (hasPrecipitation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            day.precipitationProbability?.let {
                Text(
                    stringResource(R.string.outlook_probability, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(
            Modifier.width(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.East,
                contentDescription = windDirectionName(day.windDirectionDominant),
                tint = windColor,
                // Šipka ukazuje, KAM vítr vane (směr z dat je odkud): +180° − 90° (ikona míří na východ).
                modifier = Modifier
                    .size(20.dp)
                    .rotate(((day.windDirectionDominant + 90) % 360).toFloat()),
            )
            Text(
                windSpeed.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (strongWind) FontWeight.Bold else FontWeight.Normal,
                color = windColor,
            )
        }
        Text(
            formatTemperature(day.temperatureMin),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        TemperatureRangeBar(
            min = day.temperatureMin,
            max = day.temperatureMax,
            scaleMin = scaleMin,
            scaleMax = scaleMax,
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .padding(horizontal = 6.dp),
        )
        Text(
            formatTemperature(day.temperatureMax),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
    }
}

/** Vodorovný pruh min–max na společné stupnici všech dní výhledu. */
@Composable
private fun TemperatureRangeBar(
    min: Double,
    max: Double,
    scaleMin: Double,
    scaleMax: Double,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val barColor: Color = WeatherTheme.colors.temperature
    Canvas(modifier) {
        val stroke = size.height
        val start = stroke / 2
        val end = size.width - stroke / 2
        val y = size.height / 2
        drawLine(trackColor, Offset(start, y), Offset(end, y), strokeWidth = stroke, cap = StrokeCap.Round)
        val range = (scaleMax - scaleMin).takeIf { it > 0 } ?: 1.0
        fun x(t: Double) = start + ((t - scaleMin) / range).toFloat().coerceIn(0f, 1f) * (end - start)
        drawLine(barColor, Offset(x(min), y), Offset(x(max), y), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun DayDetail(day: DailyWeather, hours: List<HourlyWeather>) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Průběh dne 00–24 h stejným meteogramem jako „Dalších 24 hodin“ na obrazovce Dnes.
        if (hours.size >= 2) {
            Row(
                Modifier.padding(top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.outlook_day_chart),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                MeteogramLegend()
            }
            Meteogram(
                hours = hours,
                markStart = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
            )
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        ) {
            DETAIL_HOURS.forEach { hour ->
                val slot = day.hours.firstOrNull { it.time.hour == hour }
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (slot != null) {
                        // Srážky za 3 h kolem daného času (hodinové hodnoty jsou úhrny za předchozí hodinu).
                        val precipitation = day.hours.filter { it.time.hour in hour - 1..hour + 1 }.sumOf { it.precipitation }
                        val hasPrecipitation = precipitation >= 0.05
                        Text(
                            "%02d".format(hour),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(formatTemperature(slot.temperature), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatPrecipitation(precipitation),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (hasPrecipitation) FontWeight.Bold else FontWeight.Normal,
                            color = if (hasPrecipitation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            day.windGustsMax?.let {
                InfoChip(
                    stringResource(R.string.outlook_gusts, it.roundToInt()),
                    color = WeatherTheme.colors.temperature,
                )
            }
            day.humidity?.let { InfoChip(stringResource(R.string.outlook_humidity, it)) }
            day.pressure?.let { InfoChip(stringResource(R.string.outlook_pressure, it.roundToInt())) }
        }
    }
}

/** Neinteraktivní M3 chip (vzhled suggestion chipu) pro doplňující hodnoty dne. */
@Composable
private fun InfoChip(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

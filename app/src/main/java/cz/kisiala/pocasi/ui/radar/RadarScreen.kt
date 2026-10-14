package cz.kisiala.pocasi.ui.radar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.NearbyPrecipitation
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.model.RadarScale
import cz.kisiala.pocasi.ui.common.message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

@Composable
fun RadarScreen(viewModel: RadarViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val centerRequests = remember { MutableSharedFlow<Place>(extraBufferCapacity = 1) }
    val locationUnavailable = stringResource(R.string.location_unavailable)

    LaunchedEffect(Unit) {
        viewModel.onRadarShown()
        // Nový snímek přibývá každých 5 min – při otevřeném radaru se ho držíme.
        while (true) {
            delay(60_000)
            viewModel.refreshIfStale()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.locateResult.collect { place ->
            if (place != null) centerRequests.tryEmit(place) else snackbarHostState.showSnackbar(locationUnavailable)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.locateMe()
    }

    val density = LocalDensity.current
    var sheetHeight by remember { mutableIntStateOf(0) }
    var overlayBottom by remember { mutableIntStateOf(0) }
    val side = with(density) { 16.dp.roundToPx() }
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    val insets = MapInsets(top = overlayBottom + side, bottom = sheetHeight + side, side = side)

    Box(modifier.fillMaxSize()) {
        RadarMap(
            radarImage = state.displayed?.image,
            radarOpacity = if (state.translucent) 0.45f else 0.8f,
            myLocation = state.myLocation,
            insets = insets,
            centerRequests = centerRequests.asSharedFlow(),
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .onSizeChanged {
                    overlayBottom = it.height + statusBarTop
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RadarSearchBar(
                time = state.displayed?.time?.let(::formatRadarTime),
                translucent = state.translucent,
                onToggleTranslucent = viewModel::toggleTranslucent,
            )
            state.nearby?.takeIf { state.latest != null }?.let { NearbyChip(it) }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            SnackbarHost(snackbarHostState)
            FloatingActionButton(
                onClick = {
                    if (viewModel.hasLocationPermission()) {
                        viewModel.locateMe()
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 16.dp, bottom = 16.dp),
            ) {
                Icon(Icons.Outlined.LocationSearching, contentDescription = stringResource(R.string.action_my_location))
            }
            RadarSheet(
                state = state,
                onTogglePlay = viewModel::togglePlayback,
                onSelectFrame = viewModel::selectFrame,
                onRetry = viewModel::retry,
                modifier = Modifier.onSizeChanged { sheetHeight = it.height },
            )
        }
    }
}

/** Plovoucí lišta ve stylu M3 search baru: název, čas snímku a menu vrstev. */
@Composable
private fun RadarSearchBar(time: String?, translucent: Boolean, onToggleTranslucent: () -> Unit) {
    var layersOpen by remember { mutableStateOf(false) }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Radar,
                contentDescription = null,
                modifier = Modifier.padding(start = 16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (time != null) stringResource(R.string.radar_title_time, time) else stringResource(R.string.radar_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(Modifier.padding(end = 4.dp)) {
                IconButton(onClick = { layersOpen = true }) {
                    Icon(Icons.Outlined.Layers, contentDescription = stringResource(R.string.action_layers))
                }
                DropdownMenu(expanded = layersOpen, onDismissRequest = { layersOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.layers_translucent)) },
                        trailingIcon = { Checkbox(checked = translucent, onCheckedChange = null) },
                        onClick = onToggleTranslucent,
                    )
                }
            }
        }
    }
}

@Composable
private fun NearbyChip(nearby: NearbyPrecipitation) {
    val icon: ImageVector
    var rotation = 0f
    val text = when (nearby) {
        is NearbyPrecipitation.Found if nearby.distanceKm > 0 -> {
            icon = Icons.AutoMirrored.Filled.ArrowForward
            rotation = arrowRotation(nearby.bearing)
            stringResource(R.string.radar_nearest_rain, nearby.distanceKm, directionAdverb(nearby.bearing))
        }
        is NearbyPrecipitation.Found -> {
            icon = Icons.Outlined.Umbrella
            stringResource(R.string.radar_rain_here)
        }
        NearbyPrecipitation.None -> {
            icon = Icons.Outlined.CheckCircle
            stringResource(R.string.precip_none)
        }
        NearbyPrecipitation.OutOfCoverage -> {
            icon = Icons.Outlined.Radar
            stringResource(R.string.precip_out_of_coverage)
        }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Spodní panel: přehrávání, posuvník času −2 h … teď a legenda intenzity. */
@Composable
private fun RadarSheet(
    state: RadarUiState,
    onTogglePlay: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 10.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
            )
            when {
                state.latest != null -> {
                    PlaybackRow(state, onTogglePlay, onSelectFrame)
                    Spacer(Modifier.height(16.dp))
                    IntensityLegend()
                }
                state.error != null && !state.isLoading -> Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.precip_error), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(state.error.message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                else -> Column(Modifier.padding(vertical = 16.dp)) {
                    Text(
                        stringResource(R.string.precip_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackRow(state: RadarUiState, onTogglePlay: () -> Unit, onSelectFrame: (Int) -> Unit) {
    val frames = state.frames
    val canPlay = frames.size > 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledIconButton(
            onClick = onTogglePlay,
            enabled = canPlay,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (state.isPlaying) R.string.action_pause else R.string.action_play),
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            val timeDescription = stringResource(R.string.radar_time_slider)
            val thumbColor = MaterialTheme.colorScheme.primary
            Slider(
                value = if (canPlay) state.index.toFloat() else 1f,
                onValueChange = { onSelectFrame(it.roundToInt()) },
                enabled = canPlay,
                valueRange = 0f..(if (canPlay) frames.lastIndex.toFloat() else 1f),
                steps = (frames.size - 2).coerceAtLeast(0),
                modifier = Modifier.semantics { contentDescription = timeDescription },
                thumb = {
                    Box(
                        Modifier
                            .size(24.dp)
                            .background(thumbColor, CircleShape),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(6.dp),
                        enabled = true,
                        drawStopIndicator = null,
                        drawTick = { _, _ -> },
                        thumbTrackGapSize = 0.dp,
                        trackInsideCornerSize = 3.dp,
                    )
                },
            )
            Row(Modifier.fillMaxWidth()) {
                val labelStyle = MaterialTheme.typography.labelLarge
                val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                Text(stringResource(R.string.radar_minus_2h), style = labelStyle, color = labelColor)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.radar_minus_1h), style = labelStyle, color = labelColor)
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.radar_now, formatRadarTime(frames.last().time)),
                    style = labelStyle,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Legenda podle barevné škály ČHMÚ (scl-dbz-mmh.png). */
@Composable
private fun IntensityLegend() {
    val scale = remember { RadarScale.colors.map { Color(0xFF000000 or it.toLong()) } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.legend_light), style = MaterialTheme.typography.labelLarge)
        Canvas(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(10.dp),
        ) {
            drawRoundRect(
                brush = Brush.horizontalGradient(scale),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
        Text(stringResource(R.string.legend_torrential), style = MaterialTheme.typography.labelLarge)
    }
}

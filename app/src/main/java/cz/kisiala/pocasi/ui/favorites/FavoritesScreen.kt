package cz.kisiala.pocasi.ui.favorites

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.data.PlaceRepository
import cz.kisiala.pocasi.model.Place
import kotlinx.coroutines.launch

/** Výška řádku – podle ní se při tažení určuje, o kolik míst se položka posunula. */
private val ROW_HEIGHT = 72.dp

/**
 * Úprava oblíbených míst: odebrání (s možností vrátit) a změna pořadí tažením za úchyt.
 * Každá změna se hned ukládá přes [PlaceRepository].
 */
@Composable
fun FavoritesScreen(places: PlaceRepository, onAddPlace: () -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        FavoritesContent(places, onAddPlace, onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoritesContent(repository: PlaceRepository, onAddPlace: () -> Unit, onClose: () -> Unit) {
    // Pracovní kopie – při tažení se mění plynule, uloží se až po puštění.
    val items = remember { mutableStateListOf<Place>().apply { addAll(repository.favorites.value) } }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val undo = stringResource(R.string.action_undo)

    fun save() = repository.setFavorites(items.toList())

    fun remove(place: Place) {
        val index = items.indexOf(place)
        if (index < 0) return
        items.removeAt(index)
        repository.removeFavorite(place)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                context.getString(R.string.favorite_removed, place.name),
                actionLabel = undo,
            )
            if (result == SnackbarResult.ActionPerformed && place !in items) {
                items.add(index.coerceAtMost(items.size), place)
                save()
            }
        }
    }

    fun move(from: Int, to: Int) {
        if (to !in items.indices || from == to) return
        items.add(to, items.removeAt(from))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (items.isEmpty()) {
                EmptyFavorites(onAddPlace)
            } else {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.favorites_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    ReorderableList(
                        items = items,
                        onMove = ::move,
                        onDragEnd = ::save,
                        onRemove = ::remove,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableList(
    items: List<Place>,
    onMove: (from: Int, to: Int) -> Unit,
    onDragEnd: () -> Unit,
    onRemove: (Place) -> Unit,
) {
    // Řádek + 1dp oddělovač.
    val rowHeightPx = with(LocalDensity.current) { (ROW_HEIGHT + 1.dp).toPx() }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val moveUp = stringResource(R.string.favorites_move_up)
    val moveDown = stringResource(R.string.favorites_move_down)

    Column {
        items.forEachIndexed { index, place ->
            // key: při přeřazení se řádek (i rozběhnuté tažení) přesune s místem, nezačne znovu.
            key(place.id) {
                val dragged = place.id == draggedId
                Surface(
                    color = if (dragged) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                    shadowElevation = if (dragged) 4.dp else 0.dp,
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .graphicsLayer { translationY = if (dragged) dragOffset else 0f }
                        .semantics {
                            // Přeřazení i bez tažení (TalkBack).
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(moveUp) { onMove(index, index - 1); onDragEnd(); true }
                                    .takeIf { index > 0 },
                                CustomAccessibilityAction(moveDown) { onMove(index, index + 1); onDragEnd(); true }
                                    .takeIf { index < items.lastIndex },
                            )
                        },
                ) {
                    Column {
                        ListItem(
                            headlineContent = { Text(place.name) },
                            supportingContent = place.region?.let { { Text(it) } },
                            leadingContent = {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = stringResource(R.string.favorites_drag),
                                    modifier = Modifier
                                        .size(48.dp)
                                        .padding(12.dp)
                                        .pointerInput(place.id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedId = place.id
                                                    dragOffset = 0f
                                                },
                                                onDragEnd = {
                                                    draggedId = null
                                                    dragOffset = 0f
                                                    onDragEnd()
                                                },
                                                onDragCancel = {
                                                    draggedId = null
                                                    dragOffset = 0f
                                                    onDragEnd()
                                                },
                                            ) { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                // Po překročení půlky řádku prohodíme se sousedem a posun přepočítáme.
                                                val current = items.indexOfFirst { it.id == place.id }
                                                if (dragOffset > rowHeightPx / 2 && current < items.lastIndex) {
                                                    onMove(current, current + 1)
                                                    dragOffset -= rowHeightPx
                                                } else if (dragOffset < -rowHeightPx / 2 && current > 0) {
                                                    onMove(current, current - 1)
                                                    dragOffset += rowHeightPx
                                                }
                                            }
                                        },
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { onRemove(place) }) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.action_remove_favorite),
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.height(ROW_HEIGHT),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavorites(onAddPlace: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            Icons.Filled.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            stringResource(R.string.favorites_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onAddPlace) { Text(stringResource(R.string.places_add)) }
    }
}

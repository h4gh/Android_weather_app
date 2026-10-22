package cz.kisiala.pocasi.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.ui.LoadError

/**
 * Celoobrazovkové vyhledávání místa; po výběru se místo vybere v aplikaci (nebo předá [onPick],
 * např. při nastavení widgetu) a hledání zavře.
 */
@Composable
fun PlaceSearchScreen(
    onClose: () -> Unit,
    onPick: ((Place) -> Unit)? = null,
    viewModel: PlaceSearchViewModel = viewModel(factory = PlaceSearchViewModel.Factory),
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // ViewModel žije s aktivitou – každé otevření začíná s prázdným polem.
        LaunchedEffect(Unit) { viewModel.onQueryChange("") }
        val state by viewModel.state.collectAsStateWithLifecycle()
        Scaffold(
            topBar = {
                SearchField(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onBack = onClose,
                    isSearching = state.isSearching,
                )
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                when {
                    state.error != null -> SearchMessage(
                        text = stringResource(
                            if (state.error == LoadError.Network) R.string.search_error_network else R.string.search_error,
                        ),
                        action = stringResource(R.string.action_retry),
                        onAction = viewModel::retry,
                    )
                    state.showsNoResults -> SearchMessage(stringResource(R.string.search_no_results, state.query.trim()))
                    state.results.isEmpty() -> SearchMessage(stringResource(R.string.search_hint))
                    else -> SearchResults(
                        results = state.results,
                        favoriteIds = state.favoriteIds,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onSelect = {
                            if (onPick != null) onPick(it) else viewModel.select(it)
                            onClose()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    isSearching: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            }
            // Pruh průběhu drží místo i bez hledání, aby obsah pod ním neposkakoval.
            Box(Modifier.height(4.dp).fillMaxWidth()) {
                if (isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<Place>,
    favoriteIds: Set<String>,
    onSelect: (Place) -> Unit,
    onToggleFavorite: (Place) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(results, key = { it.id }) { place ->
            ListItem(
                headlineContent = { Text(place.name) },
                supportingContent = place.region?.let { { Text(it) } },
                leadingContent = { Icon(Icons.Outlined.Place, contentDescription = null) },
                trailingContent = {
                    val favorite = place.id in favoriteIds
                    IconButton(onClick = { onToggleFavorite(place) }) {
                        Icon(
                            if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(
                                if (favorite) R.string.action_remove_favorite else R.string.action_add_favorite,
                            ),
                            tint = if (favorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onSelect(place) },
            )
            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
        item {
            Text(
                stringResource(R.string.search_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SearchMessage(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

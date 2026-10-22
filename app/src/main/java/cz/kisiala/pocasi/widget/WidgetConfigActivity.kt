package cz.kisiala.pocasi.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.ui.search.PlaceSearchScreen
import cz.kisiala.pocasi.ui.theme.PocasiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Výběr místa při přidání (a přenastavení) widgetu: Moje poloha, oblíbená místa, nebo vyhledat.
 * Bez potvrzení výběru se widget nepřidá (RESULT_CANCELED).
 */
class WidgetConfigActivity : ComponentActivity() {

    private val appWidgetId by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED, resultIntent())
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val container = appContainer()
        val themeModes = container.settingsRepository.themeMode
        val initialMode = runBlocking { themeModes.first() }
        setContent {
            val mode by themeModes.collectAsStateWithLifecycle(initialMode)
            PocasiTheme(darkTheme = mode.isDark(isSystemInDarkTheme())) {
                val favorites by container.placeRepository.favorites.collectAsStateWithLifecycle()
                val lastLocation by container.placeRepository.lastLocation.collectAsStateWithLifecycle(null)
                WidgetConfigScreen(
                    lastLocationName = lastLocation?.name,
                    favorites = favorites,
                    onPick = ::finishWith,
                    onCancel = ::finish,
                )
            }
        }
    }

    private var saving = false

    private fun finishWith(place: Place) {
        if (saving) return
        saving = true
        lifecycleScope.launch {
            val manager = GlanceAppWidgetManager(this@WidgetConfigActivity)
            val glanceId = manager.getGlanceIdBy(appWidgetId)
            WidgetUpdater.configure(this@WidgetConfigActivity, glanceId, place)
            // Widget může být „Počasí“ i „Výhled“ – překreslíme ten, kterému id patří.
            val provider = AppWidgetManager.getInstance(this@WidgetConfigActivity).getAppWidgetInfo(appWidgetId)?.provider
            if (provider?.className == OutlookWidgetReceiver::class.java.name) {
                OutlookWidget().update(this@WidgetConfigActivity, glanceId)
            } else {
                WeatherWidget().update(this@WidgetConfigActivity, glanceId)
            }
            setResult(RESULT_OK, resultIntent())
            finish()
        }
    }

    private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfigScreen(
    lastLocationName: String?,
    favorites: List<Place>,
    onPick: (Place) -> Unit,
    onCancel: () -> Unit,
) {
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val myLocationLabel = stringResource(R.string.place_my_location)
    val pick: (Place) -> Unit = {
        saving = true
        onPick(it)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_config_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        if (saving) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                ListItem(
                    headlineContent = { Text(myLocationLabel) },
                    supportingContent = {
                        Text(
                            lastLocationName?.let { stringResource(R.string.widget_config_location_known, it) }
                                ?: stringResource(R.string.widget_config_location_unknown),
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.MyLocation, contentDescription = null) },
                    modifier = Modifier.clickable {
                        pick(Place(Place.CURRENT_LOCATION_ID, myLocationLabel, 0.0, 0.0, Place.Kind.CurrentLocation))
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            items(favorites, key = { it.id }) { place ->
                ListItem(
                    headlineContent = { Text(place.name) },
                    supportingContent = place.region?.let { { Text(it) } },
                    leadingContent = { Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { pick(place) },
                )
            }
            item {
                if (favorites.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.widget_config_search)) },
                    leadingContent = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    modifier = Modifier.clickable { showSearch = true },
                )
            }
        }
    }

    if (showSearch) {
        PlaceSearchScreen(onClose = { showSearch = false }, onPick = pick)
    }
}

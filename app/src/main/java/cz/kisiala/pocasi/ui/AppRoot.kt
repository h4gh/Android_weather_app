package cz.kisiala.pocasi.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.kisiala.pocasi.AppContainer
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.ThemeMode
import cz.kisiala.pocasi.ui.common.AboutDialog
import cz.kisiala.pocasi.ui.common.AppMenuActions
import cz.kisiala.pocasi.ui.common.AppearanceDialog
import cz.kisiala.pocasi.ui.outlook.OutlookScreen
import cz.kisiala.pocasi.ui.radar.RadarScreen
import cz.kisiala.pocasi.ui.radar.RadarViewModel
import cz.kisiala.pocasi.ui.today.TodayScreen
import kotlinx.coroutines.launch

enum class AppTab(@StringRes val label: Int, val icon: ImageVector) {
    Today(R.string.tab_today, Icons.Outlined.LightMode),
    Outlook(R.string.tab_outlook, Icons.Outlined.CalendarToday),
    Radar(R.string.tab_radar, Icons.Outlined.Map),
}

@Composable
fun AppRoot(container: AppContainer) {
    // Obrazovky v HorizontalPageru – swipe i spodní lišta mění stránku.
    val pagerState = rememberPagerState { AppTab.entries.size }
    val scope = rememberCoroutineScope()
    fun open(tab: AppTab) {
        scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
    }
    // Jedna instance pro Dnes i Výhled – sdílí stav i jediný dotaz na předpověď.
    val forecastViewModel: ForecastViewModel = viewModel(factory = ForecastViewModel.Factory)
    // Radar sdílí Dnes (karta „Nejbližší srážky“) i Radar – snímky se stahují jen jednou.
    val radarViewModel: RadarViewModel = viewModel(factory = RadarViewModel.Factory)
    LifecycleStartEffect(radarViewModel) {
        radarViewModel.refreshIfStale()
        onStopOrDispose {}
    }
    LocationPermissionEffect(container)

    // Menu ⋮ je na všech obrazovkách, dialogy zobrazuje AppRoot.
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val menuActions = remember { AppMenuActions(onAppearance = { showAppearance = true }, onAbout = { showAbout = true }) }
    if (showAppearance) {
        val settings = container.settingsRepository
        val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.System)
        AppearanceDialog(
            selected = themeMode,
            onSelect = { container.appScope.launch { settings.setThemeMode(it) } },
            onDismiss = { showAppearance = false },
        )
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    val selected = item.ordinal == pagerState.targetPage
                    NavigationBarItem(
                        selected = selected,
                        onClick = { open(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = {
                            Text(
                                stringResource(item.label),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            // Na Radaru patří gesta mapě – odtud se odchází jen spodní lištou. settledPage (ne currentPage),
            // aby se swipe na Radar nepřerušil uprostřed gesta.
            userScrollEnabled = pagerState.settledPage != AppTab.Radar.ordinal,
            key = { AppTab.entries[it] },
        ) { page ->
            AppPage(
                tab = AppTab.entries[page],
                innerPadding = innerPadding,
                forecastViewModel = forecastViewModel,
                radarViewModel = radarViewModel,
                onOpenRadar = { open(AppTab.Radar) },
                menuActions = menuActions,
            )
        }
    }
}

@Composable
private fun AppPage(
    tab: AppTab,
    innerPadding: PaddingValues,
    forecastViewModel: ForecastViewModel,
    radarViewModel: RadarViewModel,
    onOpenRadar: () -> Unit,
    menuActions: AppMenuActions,
) {
    val modifier = Modifier
        .padding(innerPadding)
        .consumeWindowInsets(innerPadding)
    when (tab) {
        AppTab.Today -> TodayScreen(
            onOpenRadar = onOpenRadar,
            menuActions = menuActions,
            modifier = modifier,
            viewModel = forecastViewModel,
            radarViewModel = radarViewModel,
        )
        AppTab.Outlook -> OutlookScreen(viewModel = forecastViewModel, menuActions = menuActions, modifier = modifier)
        // Mapa je celoobrazovková – pod stavovým řádkem, odsazená jen od Navigation Baru.
        AppTab.Radar -> {
            val bottom = PaddingValues(bottom = innerPadding.calculateBottomPadding())
            RadarScreen(
                viewModel = radarViewModel,
                menuActions = menuActions,
                modifier = Modifier
                    .padding(bottom)
                    .consumeWindowInsets(bottom),
            )
        }
    }
}

/** Při prvním spuštění požádá o polohu; po odpovědi (i odmítnutí) zjistí místo. */
@Composable
private fun LocationPermissionEffect(container: AppContainer) {
    val places = container.placeRepository
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.appScope.launch { places.refreshCurrentLocation() }
    }
    // Přežije otočení obrazovky, aby se dialog s oprávněním nevyvolal dvakrát.
    var requested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        when {
            places.isLocationResolved -> Unit
            // S oprávněním zjistíme polohu; po již položeném dotazu (např. obnova po ukončení
            // procesu) bez oprávnění rovnou spadneme na výchozí místo.
            container.locationProvider.hasPermission() || requested -> places.refreshCurrentLocation()
            else -> {
                requested = true
                launcher.launch(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                )
            }
        }
    }
}

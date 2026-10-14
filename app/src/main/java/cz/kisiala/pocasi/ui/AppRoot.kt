package cz.kisiala.pocasi.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import cz.kisiala.pocasi.AppContainer
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.ui.common.ComingSoonScreen
import cz.kisiala.pocasi.ui.outlook.OutlookScreen
import cz.kisiala.pocasi.ui.today.TodayScreen
import kotlinx.coroutines.launch

enum class AppTab(@StringRes val label: Int, val icon: ImageVector) {
    Today(R.string.tab_today, Icons.Outlined.LightMode),
    Outlook(R.string.tab_outlook, Icons.Outlined.CalendarToday),
    Radar(R.string.tab_radar, Icons.Outlined.Map),
}

@Composable
fun AppRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Today) }
    // Jedna instance pro Dnes i Výhled – sdílí stav i jediný dotaz na předpověď.
    val forecastViewModel: ForecastViewModel = viewModel(factory = ForecastViewModel.Factory)
    LocationPermissionEffect(container)

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    val selected = item == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = item },
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
        val modifier = Modifier
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
        when (tab) {
            AppTab.Today -> TodayScreen(
                onOpenRadar = { tab = AppTab.Radar },
                modifier = modifier,
                viewModel = forecastViewModel,
            )
            AppTab.Outlook -> OutlookScreen(viewModel = forecastViewModel, modifier = modifier)
            AppTab.Radar -> ComingSoonScreen(tab = tab, modifier = modifier)
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

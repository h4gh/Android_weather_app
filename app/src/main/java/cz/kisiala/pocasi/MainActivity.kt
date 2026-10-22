package cz.kisiala.pocasi

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.kisiala.pocasi.ui.AppRoot
import cz.kisiala.pocasi.ui.theme.PocasiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PocasiApp).container
        val themeModes = container.settingsRepository.themeMode
        // Malé čtení z DataStore – ať se při zvoleném vzhledu první snímek nevykreslí v opačném.
        val initialThemeMode = runBlocking { themeModes.first() }
        setContent {
            val themeMode by themeModes.collectAsStateWithLifecycle(initialThemeMode)
            val darkTheme = themeMode.isDark(isSystemInDarkTheme())
            // Ikony ve stavovém a navigačním řádku podle zvoleného vzhledu, ne podle systému.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }
            PocasiTheme(darkTheme = darkTheme) {
                AppRoot(container)
            }
        }
    }

    private companion object {
        // Výchozí scrimy navigačního řádku z enableEdgeToEdge().
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}

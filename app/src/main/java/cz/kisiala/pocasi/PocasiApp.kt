package cz.kisiala.pocasi

import android.app.Application
import android.content.res.Configuration
import cz.kisiala.pocasi.data.AppLocales
import cz.kisiala.pocasi.notifications.WeatherNotifier
import cz.kisiala.pocasi.widget.WidgetUpdater
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class PocasiApp : Application() {
    lateinit var container: AppContainer
        private set

    private var locales = ""

    override fun onCreate() {
        super.onCreate()
        AppLocales.applyLegacy(this)
        locales = resources.configuration.locales.toLanguageTags()
        container = AppContainer(this)
        // Mapové dlaždice OpenFreeMap nepotřebují API klíč.
        MapLibre.getInstance(this)
    }

    /** Po změně jazyka (v aplikaci i v systému) přeložíme i widgety a název kanálu upozornění. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newLocales = newConfig.locales.toLanguageTags()
        if (newLocales == locales) return
        locales = newLocales
        onLanguageChanged()
    }

    fun onLanguageChanged() {
        WeatherNotifier.ensureChannel(this)
        container.appScope.launch { WidgetUpdater.redrawAll(this@PocasiApp) }
    }
}

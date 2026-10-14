package cz.kisiala.pocasi

import android.app.Application
import org.maplibre.android.MapLibre

class PocasiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Mapové dlaždice OpenFreeMap nepotřebují API klíč.
        MapLibre.getInstance(this)
    }
}

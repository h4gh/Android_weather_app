package cz.kisiala.pocasi.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import cz.kisiala.pocasi.PocasiApp
import cz.kisiala.pocasi.data.decodePlace
import cz.kisiala.pocasi.data.encodePlace
import cz.kisiala.pocasi.model.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/** Stav jednoho widgetu (Glance preferences): zvolené místo a poslední data předpovědi. */
object WidgetState {
    /** Zakódované místo, nebo [CURRENT_LOCATION]; chybí = widget ještě není nastavený. */
    val PLACE = stringPreferencesKey("place")
    val DATA = stringPreferencesKey("data")
    /** Poslední načtení selhalo (zobrazují se starší data, nebo chybová hláška). */
    val FAILED = booleanPreferencesKey("failed")
    /** Čas posledního úspěšného načtení (ms). */
    val UPDATED_AT = longPreferencesKey("updated_at")

    const val CURRENT_LOCATION = "current"

    private val json = Json { ignoreUnknownKeys = true }

    fun data(prefs: Preferences): WidgetData? =
        prefs[DATA]?.let { runCatching { json.decodeFromString(WidgetData.serializer(), it) }.getOrNull() }

    fun encode(data: WidgetData): String = json.encodeToString(WidgetData.serializer(), data)

    fun encodeChoice(place: Place): String = if (place.isCurrentLocation) CURRENT_LOCATION else encodePlace(place)
}

/** Načtení předpovědi pro widgety – při obnově (30 min), po nastavení widgetu a když aplikace zjistí novou polohu. */
object WidgetUpdater {

    /** Data mladší než tohle se při překreslení znovu nestahují (systém widget obnovuje po 30 min). */
    private const val FRESH_MS = 25 * 60 * 1000L

    /** Obnoví data, jen pokud chybí nebo jsou starší než [FRESH_MS] – volá se při každém vykreslení widgetu. */
    suspend fun refreshIfStale(context: Context, id: GlanceId) {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val updatedAt = prefs[WidgetState.UPDATED_AT] ?: 0L
        if (prefs[WidgetState.DATA] == null || System.currentTimeMillis() - updatedAt > FRESH_MS) refresh(context, id)
    }

    /** Stáhne předpověď pro místo widgetu a uloží ji do jeho stavu; při chybě nechá předchozí data. */
    suspend fun refresh(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PocasiApp).container
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val choice = prefs[WidgetState.PLACE] ?: return
        val isCurrent = choice == WidgetState.CURRENT_LOCATION
        // Poloha jen z aplikace (poslední zjištěná) – na pozadí ji bez „Povolit vždy“ zjistit nelze.
        val place = if (isCurrent) {
            container.placeRepository.lastLocation.first() ?: Place.FrydekMistek
        } else {
            decodePlace(choice) ?: return
        }
        val data = try {
            WidgetData.from(container.weatherRepository.forecast(place), place.name, isCurrent)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
            state.toMutablePreferences().apply {
                if (data != null) {
                    this[WidgetState.DATA] = WidgetState.encode(data)
                    this[WidgetState.UPDATED_AT] = System.currentTimeMillis()
                }
                this[WidgetState.FAILED] = data == null
            }
        }
    }

    /** Nastaví místo nového (nebo přenastaveného) widgetu a hned ho naplní daty. */
    suspend fun configure(context: Context, id: GlanceId, place: Place) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { state ->
            state.toMutablePreferences().apply {
                this[WidgetState.PLACE] = WidgetState.encodeChoice(place)
                remove(WidgetState.DATA)
            }
        }
        refresh(context, id)
    }

    /** Obnoví widgety obou druhů; [onlyCurrentLocation] = jen ty s „Moje poloha“ (aplikace zjistila novou polohu). */
    suspend fun refreshAll(context: Context, onlyCurrentLocation: Boolean = false) {
        val manager = GlanceAppWidgetManager(context)
        for (widget in allWidgets()) {
            for (id in manager.getGlanceIds(widget.javaClass)) {
                if (onlyCurrentLocation) {
                    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                    if (prefs[WidgetState.PLACE] != WidgetState.CURRENT_LOCATION) continue
                }
                refresh(context, id)
                widget.update(context, id)
            }
        }
    }

    /** Překreslí widgety bez nového dotazu (např. po změně vzhledu v aplikaci). */
    suspend fun redrawAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        for (widget in allWidgets()) {
            for (id in manager.getGlanceIds(widget.javaClass)) widget.update(context, id)
        }
    }

    private fun allWidgets(): List<GlanceAppWidget> = listOf(WeatherWidget(), OutlookWidget())
}

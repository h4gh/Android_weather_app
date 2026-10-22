package cz.kisiala.pocasi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cz.kisiala.pocasi.model.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.placesDataStore by preferencesDataStore(name = "places")

/** Trvalé uložení oblíbených míst (v pořadí) a naposledy vybraného místa – JSON v DataStore. */
class PlaceStore(context: Context) {
    private val dataStore = context.applicationContext.placesDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(StoredPlace.serializer())

    val favorites: Flow<List<Place>> = dataStore.data.map { prefs ->
        prefs[FAVORITES]?.let { decode { json.decodeFromString(listSerializer, it) } }.orEmpty()
            .map { it.toPlace(Place.Kind.Saved) }
    }

    /** Poslední zjištěná poloha zařízení (s názvem) – widget s „Moje poloha“ nesmí polohu zjišťovat na pozadí. */
    val lastLocation: Flow<Place?> = dataStore.data.map { prefs ->
        prefs[LAST_LOCATION]?.let(::decodePlace)?.copy(kind = Place.Kind.CurrentLocation)
    }

    /** Naposledy vybrané místo (i hledané, které není v oblíbených); `null` = zatím nic. */
    val lastSelected: Flow<Place?> = dataStore.data.map { prefs ->
        prefs[LAST_SELECTED]?.let { decode { json.decodeFromString(StoredPlace.serializer(), it) } }
            ?.let { it.toPlace(it.kind?.let(::kindOf) ?: Place.Kind.Search) }
    }

    suspend fun setFavorites(places: List<Place>) {
        dataStore.edit { it[FAVORITES] = json.encodeToString(listSerializer, places.map(::StoredPlace)) }
    }

    suspend fun setLastLocation(place: Place) {
        dataStore.edit { it[LAST_LOCATION] = encodePlace(place) }
    }

    suspend fun setLastSelected(place: Place) {
        dataStore.edit { it[LAST_SELECTED] = json.encodeToString(StoredPlace.serializer(), StoredPlace(place)) }
    }

    /** Poškozená nebo stará data nesmí shodit aplikaci – bereme je jako prázdná. */
    private inline fun <T> decode(block: () -> T): T? = runCatching(block).getOrNull()

    private fun kindOf(name: String): Place.Kind? = Place.Kind.entries.firstOrNull { it.name == name }

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val LAST_SELECTED = stringPreferencesKey("last_selected")
        val LAST_LOCATION = stringPreferencesKey("last_location")
    }
}

@Serializable
private data class StoredPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val region: String? = null,
    val kind: String? = null,
) {
    constructor(place: Place) : this(place.id, place.name, place.latitude, place.longitude, place.region, place.kind.name)

    fun toPlace(kind: Place.Kind) = Place(id, name, latitude, longitude, kind, region)
}

private val placeJson = Json { ignoreUnknownKeys = true }

/** Místo jako JSON (i s druhem) – pro uložení mimo [PlaceStore], např. ve stavu widgetu. */
fun encodePlace(place: Place): String = placeJson.encodeToString(StoredPlace.serializer(), StoredPlace(place))

fun decodePlace(json: String): Place? = runCatching {
    val stored = placeJson.decodeFromString(StoredPlace.serializer(), json)
    stored.toPlace(Place.Kind.entries.firstOrNull { it.name == stored.kind } ?: Place.Kind.Search)
}.getOrNull()

package cz.kisiala.pocasi.data

import cz.kisiala.pocasi.data.remote.GeocodingApi
import cz.kisiala.pocasi.data.remote.GeocodingResult
import cz.kisiala.pocasi.model.Place

/**
 * Vyhledávání míst podle názvu – jen Česko a okolní státy, česká místa první.
 * Názvy (hlavně států a krajů) v jazyce aplikace [language] („cs“ / „en“).
 */
class PlaceSearchRepository(private val api: GeocodingApi, private val language: () -> String) {

    suspend fun search(query: String): List<Place> {
        val language = language()
        val results = api.search(query.trim(), language = language).results
            .filter { it.countryCode in COUNTRIES }
        // sortedBy je stabilní – v rámci Česka i okolí zůstane pořadí API (relevance, velikost obce).
        return results.sortedBy { if (it.countryCode == CZECHIA) 0 else 1 }
            .take(MAX_RESULTS)
            .map { it.toPlace(czech = language == "cs") }
    }

    private fun GeocodingResult.toPlace(czech: Boolean) = Place(
        id = "geo-$id",
        name = name,
        latitude = latitude,
        longitude = longitude,
        kind = Place.Kind.Search,
        region = region(czech),
    )

    /** „Moravskoslezský kraj“, u zahraničí „Slezské vojvodství, Polsko“. */
    private fun GeocodingResult.region(czech: Boolean): String? {
        if (countryCode == CZECHIA) {
            val region = admin1 ?: return country
            // GeoNames uvádí české kraje bez slova „kraj“ („Moravskoslezský“); Praha a Vysočina ho nepotřebují.
            return if (czech && region.endsWith("ý")) "$region kraj" else region
        }
        return listOfNotNull(admin1, country).joinToString(", ").ifEmpty { null }
    }

    private companion object {
        const val CZECHIA = "CZ"
        val COUNTRIES = setOf(CZECHIA, "SK", "PL", "AT", "DE")
        const val MAX_RESULTS = 12
    }
}

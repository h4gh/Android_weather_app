package cz.kisiala.pocasi.data

import cz.kisiala.pocasi.data.remote.GeocodingApi
import cz.kisiala.pocasi.data.remote.GeocodingResult
import cz.kisiala.pocasi.model.Place

/** Vyhledávání míst podle názvu – jen Česko a okolní státy, česká místa první. */
class PlaceSearchRepository(private val api: GeocodingApi) {

    suspend fun search(query: String): List<Place> {
        val results = api.search(query.trim()).results
            .filter { it.countryCode in COUNTRIES }
        // sortedBy je stabilní – v rámci Česka i okolí zůstane pořadí API (relevance, velikost obce).
        return results.sortedBy { if (it.countryCode == CZECHIA) 0 else 1 }
            .take(MAX_RESULTS)
            .map { it.toPlace() }
    }

    private fun GeocodingResult.toPlace() = Place(
        id = "geo-$id",
        name = name,
        latitude = latitude,
        longitude = longitude,
        kind = Place.Kind.Search,
        region = region(),
    )

    /** „Moravskoslezský kraj“, u zahraničí „Slezské vojvodství, Polsko“. */
    private fun GeocodingResult.region(): String? {
        if (countryCode == CZECHIA) {
            val region = admin1 ?: return country
            // GeoNames uvádí české kraje bez slova „kraj“ („Moravskoslezský“); Praha a Vysočina ho nepotřebují.
            return if (region.endsWith("ý")) "$region kraj" else region
        }
        return listOfNotNull(admin1, country).joinToString(", ").ifEmpty { null }
    }

    private companion object {
        const val CZECHIA = "CZ"
        val COUNTRIES = setOf(CZECHIA, "SK", "PL", "AT", "DE")
        const val MAX_RESULTS = 12
    }
}

package cz.kisiala.pocasi.model

/**
 * Místo, pro které se zobrazuje předpověď.
 *
 * [id] je stabilní identifikátor – pro aktuální polohu [CURRENT_LOCATION_ID],
 * pro místa z vyhledávání `geo-<GeoNames id>`.
 */
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val kind: Kind,
    /** Upřesnění pod názvem (kraj, u zahraničí i stát); `null` u polohy a záložního místa. */
    val region: String? = null,
) {
    enum class Kind {
        /** Poloha zařízení z GPS / sítě. */
        CurrentLocation,

        /** Záložní místo, když poloha není k dispozici. */
        Default,

        /** Místo vybrané ve vyhledávání. */
        Search,

        /** Místo uložené uživatelem (připravujeme). */
        Saved,
    }

    val isCurrentLocation: Boolean get() = kind == Kind.CurrentLocation

    companion object {
        const val CURRENT_LOCATION_ID = "current-location"

        val FrydekMistek = Place(
            id = "default-frydek-mistek",
            name = "Frýdek-Místek",
            latitude = 49.6833,
            longitude = 18.35,
            kind = Kind.Default,
        )
    }
}

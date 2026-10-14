package cz.kisiala.pocasi.model

/**
 * Místo, pro které se zobrazuje předpověď.
 *
 * [id] je stabilní identifikátor – pro aktuální polohu [CURRENT_LOCATION_ID],
 * pro uložená (oblíbená) místa ho v budoucnu přidělí úložiště.
 */
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val kind: Kind,
) {
    enum class Kind {
        /** Poloha zařízení z GPS / sítě. */
        CurrentLocation,

        /** Výchozí místo, když poloha není k dispozici. */
        Default,

        /** Místo uložené uživatelem (připravujeme). */
        Saved,
    }

    val isCurrentLocation: Boolean get() = kind == Kind.CurrentLocation

    companion object {
        const val CURRENT_LOCATION_ID = "current-location"

        val Prague = Place(
            id = "default-praha",
            name = "Praha",
            latitude = 50.0755,
            longitude = 14.4378,
            kind = Kind.Default,
        )
    }
}

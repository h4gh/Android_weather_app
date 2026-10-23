package cz.kisiala.pocasi.model

import java.time.LocalDateTime

/** Rada k deštníku na nejbližší hodinu – z radaru (co padá teď) a předpovědi po 15 min. */
enum class UmbrellaAdvice {
    /** Radar ukazuje srážky přímo v místě. */
    RainingNow,

    /** Srážky jsou na radaru blízko, nebo je předpověď čeká do hodiny. */
    RainLikely,

    /** V příští hodině se srážky nečekají. */
    NotNeeded;

    companion object {
        /** Srážky do této vzdálenosti mohou do hodiny dorazit (přeháňky se běžně posouvají 20–40 km/h). */
        const val NEAR_KM = 10

        /** Úhrn za hodinu, od kterého už deštník doporučujeme (mm). */
        private const val RAIN_MM = 0.2

        fun from(forecast: Forecast, radar: NearbyPrecipitation?): UmbrellaAdvice {
            val found = radar as? NearbyPrecipitation.Found
            return when {
                found != null && found.distanceKm == 0 -> RainingNow
                found != null && found.distanceKm <= NEAR_KM -> RainLikely
                nextHourPrecipitation(forecast) >= RAIN_MM -> RainLikely
                else -> NotNeeded
            }
        }

        /** Předpovězený úhrn srážek (mm) na příští hodinu od aktuálního stavu. */
        fun nextHourPrecipitation(forecast: Forecast): Double {
            val now = forecast.current.time
            val end = now.plusHours(1)
            // Čtvrthodina končící časem t pokrývá (t − 15 min, t] – bereme ty, které zasahují do příští hodiny.
            val quarters = forecast.quarterHours.filter { it.time.isAfter(now) && it.time.minusMinutes(15).isBefore(end) }
            if (quarters.isNotEmpty()) return quarters.sumOf { it.precipitation }
            // Bez dat po 15 min (výpadek modelu) bereme hodinovou předpověď.
            return forecast.next(1).drop(1).sumOf { it.precipitation }
        }

        /** Začátek hodiny, kdy dnes později (po příští hodině) naprší alespoň [LATER_MM]; jinak `null`. */
        fun laterToday(forecast: Forecast): LocalDateTime? {
            val now = forecast.current.time
            // Hodinová hodnota je úhrn za předchozí hodinu – začátek srážek je o hodinu dřív.
            return forecast.hourly.map { it.time.minusHours(1) to it.precipitation }.firstOrNull { (start, mm) ->
                !start.isBefore(now.plusHours(1)) && start.toLocalDate() == now.toLocalDate() && mm >= LATER_MM
            }?.first
        }

        private const val LATER_MM = 0.3
    }
}

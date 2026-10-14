package cz.kisiala.pocasi.model

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Geometrie radarového kompozitu ČHMÚ (PNG v Mercatoru EPSG:3857, 1 px ≈ 1 km).
 *
 * Celý obrázek pokrývá E 11,267°–20,770°, N 48,047°–52,167°, data ale jen E 11,267°–19,624°,
 * N 48,047°–51,458° – zbytek (pravý a horní pruh) jsou popisky a rámečky, proto ho ořezáváme.
 */
object RadarGrid {
    const val IMAGE_WIDTH = 680
    const val IMAGE_HEIGHT = 460

    private const val IMAGE_WEST = 11.267
    private const val IMAGE_EAST = 20.770
    private const val IMAGE_SOUTH = 48.047
    private const val IMAGE_NORTH = 52.167
    private const val DATA_EAST = 19.624
    private const val DATA_NORTH = 51.458

    /** Ořez obrázku na oblast s daty (v pixelech celého obrázku). */
    val cropLeft = 0
    val cropTop = imageY(DATA_NORTH).roundToInt()
    val cropRight = imageX(DATA_EAST).roundToInt()
    val cropBottom = IMAGE_HEIGHT
    val width = cropRight - cropLeft
    val height = cropBottom - cropTop

    /** Hranice ořezaného obrázku – rohy pro překryv na mapě. */
    val west = lonAt(cropLeft.toDouble())
    val east = lonAt(cropRight.toDouble())
    val north = latAt(cropTop.toDouble())
    val south = latAt(cropBottom.toDouble())

    /** Sloupec v ořezaném obrázku (může být mimo rozsah). */
    fun x(longitude: Double): Double = imageX(longitude) - cropLeft

    /** Řádek v ořezaném obrázku (může být mimo rozsah). */
    fun y(latitude: Double): Double = imageY(latitude) - cropTop

    /** Zeměpisná délka středu pixelu ořezaného obrázku. */
    fun longitude(x: Int): Double = lonAt(x + cropLeft + 0.5)

    /** Zeměpisná šířka středu pixelu ořezaného obrázku. */
    fun latitude(y: Int): Double = latAt(y + cropTop + 0.5)

    private fun imageX(lon: Double) = (lon - IMAGE_WEST) / (IMAGE_EAST - IMAGE_WEST) * IMAGE_WIDTH

    private fun imageY(lat: Double) =
        (mercator(IMAGE_NORTH) - mercator(lat)) / (mercator(IMAGE_NORTH) - mercator(IMAGE_SOUTH)) * IMAGE_HEIGHT

    private fun lonAt(px: Double) = IMAGE_WEST + px / IMAGE_WIDTH * (IMAGE_EAST - IMAGE_WEST)

    private fun latAt(py: Double): Double {
        val m = mercator(IMAGE_NORTH) - py / IMAGE_HEIGHT * (mercator(IMAGE_NORTH) - mercator(IMAGE_SOUTH))
        return Math.toDegrees(2 * kotlin.math.atan(exp(m)) - PI / 2)
    }

    private fun mercator(lat: Double) = ln(tan(PI / 4 + Math.toRadians(lat) / 2))
}

/**
 * Barevná škála ČHMÚ (scl-dbz-mmh.png): 15 barev po 4 dBZ, od 4 dBZ (index 0) po 60 dBZ.
 * Odhad intenzity podle Marshall–Palmer: 7 dBZ ≈ 0,1 mm/h, 23 ≈ 1, 39 ≈ 10, 55 ≈ 100 mm/h.
 */
object RadarScale {
    val colors = intArrayOf(
        0x380070, 0x3000A8, 0x0000FC, 0x006CC0, 0x00A000,
        0x00BC00, 0x34D800, 0x9CDC00, 0xE0DC00, 0xFCB000,
        0xFC8400, 0xFC5800, 0xFC0000, 0xA00000, 0xFCFCFC,
    )

    /** Úroveň 1..15 pro barvu pixelu (RGB bez alfy), 0 = barva není ze škály. */
    fun level(rgb: Int): Int {
        val index = colors.indexOf(rgb and 0xFFFFFF)
        return if (index < 0) 0 else index + 1
    }

    fun dbz(level: Int): Int = level * 4
}

enum class PrecipitationIntensity {
    Light, Moderate, Heavy, Torrential;

    companion object {
        fun fromLevel(level: Int): PrecipitationIntensity = when (RadarScale.dbz(level)) {
            in 0..20 -> Light // do ~1 mm/h
            in 24..36 -> Moderate // do ~6 mm/h
            in 40..48 -> Heavy // do ~25 mm/h
            else -> Torrential
        }
    }
}

/** Výsledek hledání nejbližších srážek kolem místa. */
sealed interface NearbyPrecipitation {
    /** [distanceKm] 0 = srážky přímo v místě; [bearing] ve stupních od severu ve směru hodin. */
    data class Found(val distanceKm: Int, val bearing: Double, val intensity: PrecipitationIntensity) :
        NearbyPrecipitation

    data object None : NearbyPrecipitation

    /** Místo leží mimo pokrytí radarového kompozitu. */
    data object OutOfCoverage : NearbyPrecipitation

    companion object {
        const val SEARCH_RADIUS_KM = 100.0

        /** Bližší srážky než tohle bereme jako „v místě“. */
        private const val HERE_KM = 2.0

        /** Okolí nejbližšího bodu, ve kterém hledáme nejsilnější srážky pro popis intenzity. */
        private const val INTENSITY_RADIUS_KM = 5.0

        /** Slabší odrazy (4 dBZ) bývají šum, srážky počítáme od 8 dBZ (≈ 0,1 mm/h). */
        private const val MIN_LEVEL = 2

        /**
         * Nejbližší srážky dopadající na zem do [SEARCH_RADIUS_KM] od místa.
         *
         * [levels] je pro každý pixel ořezaného kompozitu úroveň 1..15 (jen sytě vykreslené, tedy
         * srážky dopadající na zem), jinak 0. Osamocené pixely (méně než 3 sousedé se srážkami)
         * ignorujeme jako šum.
         */
        fun find(levels: ByteArray, latitude: Double, longitude: Double): NearbyPrecipitation {
            val w = RadarGrid.width
            val h = RadarGrid.height
            val cx = RadarGrid.x(longitude)
            val cy = RadarGrid.y(latitude)
            if (cx < 0 || cy < 0 || cx >= w || cy >= h) return OutOfCoverage

            fun level(x: Int, y: Int) = if (x in 0 until w && y in 0 until h) levels[y * w + x].toInt() else 0
            fun isRain(x: Int, y: Int): Boolean {
                if (level(x, y) < MIN_LEVEL) return false
                var neighbours = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if ((dx != 0 || dy != 0) && level(x + dx, y + dy) >= MIN_LEVEL) neighbours++
                }
                return neighbours >= 3
            }

            // Pixel má na našich šířkách cca 1 × 1 km → okno bereme s malou rezervou.
            val reach = (SEARCH_RADIUS_KM / 0.9).toInt()
            val x0 = (cx.toInt() - reach).coerceAtLeast(0)
            val x1 = (cx.toInt() + reach).coerceAtMost(w - 1)
            val y0 = (cy.toInt() - reach).coerceAtLeast(0)
            val y1 = (cy.toInt() + reach).coerceAtMost(h - 1)

            var bestDistance = Double.MAX_VALUE
            var bestX = -1
            var bestY = -1
            for (y in y0..y1) {
                val lat = RadarGrid.latitude(y)
                for (x in x0..x1) {
                    if (!isRain(x, y)) continue
                    val d = distanceKm(latitude, longitude, lat, RadarGrid.longitude(x))
                    if (d < bestDistance) {
                        bestDistance = d
                        bestX = x
                        bestY = y
                    }
                }
            }
            if (bestX < 0 || bestDistance > SEARCH_RADIUS_KM) return None

            val bestLat = RadarGrid.latitude(bestY)
            val bestLon = RadarGrid.longitude(bestX)
            var maxLevel = 0
            val r = (INTENSITY_RADIUS_KM / 0.9).toInt()
            for (y in bestY - r..bestY + r) for (x in bestX - r..bestX + r) {
                val l = level(x, y)
                if (l > maxLevel &&
                    distanceKm(bestLat, bestLon, RadarGrid.latitude(y), RadarGrid.longitude(x)) <= INTENSITY_RADIUS_KM
                ) maxLevel = l
            }
            val here = bestDistance < HERE_KM
            return Found(
                distanceKm = if (here) 0 else roundDistance(bestDistance),
                bearing = if (here) 0.0 else bearing(latitude, longitude, bestLat, bestLon),
                intensity = PrecipitationIntensity.fromLevel(maxLevel),
            )
        }

        /** Do 20 km po kilometrech, dál po 5 km – přesnější číslo by budilo zdání přesnosti. */
        private fun roundDistance(km: Double): Int =
            if (km < 20) km.roundToInt().coerceAtLeast(1) else (km / 5).roundToInt() * 5

        private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * 6371.0 * atan2(sqrt(a), sqrt(1 - a))
        }

        private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)
            val y = sin(dLon) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }
    }
}

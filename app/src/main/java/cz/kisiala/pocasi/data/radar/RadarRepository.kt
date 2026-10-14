package cz.kisiala.pocasi.data.radar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import cz.kisiala.pocasi.model.RadarGrid
import cz.kisiala.pocasi.model.RadarScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Jeden snímek radaru, už ořezaný na oblast s daty ([RadarGrid]).
 *
 * [image] je připravený překryv pro mapu (bez rámečků a popisků), [levels] úroveň škály 1..15
 * pro každý pixel se srážkami dopadajícími na zem (sytě vykreslené), jinak 0.
 */
class RadarFrame(val time: Instant, val image: Bitmap, val levels: ByteArray)

class RadarUnavailableException : IOException("Žádný radarový snímek za poslední 2 h")

/** Maskované kompozity MAX_Z z opendata.chmi.cz; snímky drží v paměti, takže se nestahují znovu. */
class RadarRepository(private val client: OkHttpClient) {

    private val cache = LinkedHashMap<Instant, RadarFrame>()
    private val cacheMutex = Mutex()

    /** Nejnovější dostupný snímek – od aktuálního času zpět po 5 min (ČHMÚ ho zveřejní s ~1 min zpožděním). */
    suspend fun latestFrame(now: Instant = Instant.now()): RadarFrame {
        var time = now.truncatedTo(ChronoUnit.MINUTES).let { it.minusSeconds(it.epochSecond % STEP.seconds) }
        val oldest = now - MAX_AGE
        while (time >= oldest) {
            frame(time)?.let { return it }
            time -= STEP
        }
        throw RadarUnavailableException()
    }

    /** Snímky pro animaci: [latest] a předchozí po 10 min za poslední 2 h (chybějící se vynechají). */
    suspend fun animationFrames(latest: RadarFrame): List<RadarFrame> = coroutineScope {
        val permits = Semaphore(4)
        (ANIMATION_FRAMES - 1 downTo 1).map { i ->
            async { permits.withPermit { runCatching { frame(latest.time - ANIMATION_STEP.multipliedBy(i.toLong())) }.getOrNull() } }
        }.awaitAll().filterNotNull() + latest
    }

    /** Snímek pro daný čas, `null`, pokud (zatím) neexistuje. */
    suspend fun frame(time: Instant): RadarFrame? {
        cacheMutex.withLock { cache[time] }?.let { return it }
        val bytes = download(url(time)) ?: return null
        val frame = withContext(Dispatchers.Default) { decode(time, bytes) }
        cacheMutex.withLock {
            cache[time] = frame
            // Stačí pár snímků navíc pro posun okna animace.
            while (cache.size > CACHE_SIZE) cache.remove(cache.keys.min())
        }
        return frame
    }

    private suspend fun download(url: String): ByteArray? = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("HTTP ${response.code}")
                else -> response.body?.bytes()
            }
        }
    }

    private fun decode(time: Instant, bytes: ByteArray): RadarFrame {
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: throw IOException("Neplatný obrázek radaru")
        val w = RadarGrid.width
        val h = RadarGrid.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, RadarGrid.cropLeft, RadarGrid.cropTop, w, h)
        source.recycle()

        val levels = ByteArray(w * h)
        for (i in pixels.indices) {
            val argb = pixels[i]
            val rgb = argb and 0xFFFFFF
            val level = RadarScale.level(rgb)
            pixels[i] = when {
                argb ushr 24 == 0 || rgb == FRAME_BLACK || rgb == NO_COVERAGE_GRAY -> 0
                level > 0 -> {
                    levels[i] = level.toByte()
                    argb or OPAQUE
                }
                // Světlejší barvy = odraz ve výšce, srážky nedopadají na zem → méně výrazně.
                else -> rgb or (0x80 shl 24)
            }
        }
        val image = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return RadarFrame(time, image, levels)
    }

    private fun url(time: Instant) = BASE_URL + "pacz2gmaps3.z_max3d." + FILE_TIME.format(time) + ".0.png"

    companion object {
        const val BASE_URL = "https://opendata.chmi.cz/meteorology/weather/radar/composite/maxz/png_masked/"
        val STEP: Duration = Duration.ofMinutes(5)
        val ANIMATION_STEP: Duration = Duration.ofMinutes(10)
        const val ANIMATION_FRAMES = 13
        private val MAX_AGE = Duration.ofHours(2)
        private const val CACHE_SIZE = ANIMATION_FRAMES + 4
        private const val OPAQUE = 0xFF shl 24
        private const val FRAME_BLACK = 0x000000
        private const val NO_COVERAGE_GRAY = 0xC4C4C4
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd.HHmm").withZone(ZoneOffset.UTC)
    }
}

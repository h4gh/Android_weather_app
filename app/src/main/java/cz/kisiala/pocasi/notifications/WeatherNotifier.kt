package cz.kisiala.pocasi.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cz.kisiala.pocasi.MainActivity
import cz.kisiala.pocasi.PocasiApp
import cz.kisiala.pocasi.R
import cz.kisiala.pocasi.model.Forecast
import cz.kisiala.pocasi.model.NearbyPrecipitation
import cz.kisiala.pocasi.model.Place
import cz.kisiala.pocasi.model.ThemeMode
import cz.kisiala.pocasi.model.UmbrellaAdvice
import cz.kisiala.pocasi.ui.radar.formatRadarTime
import cz.kisiala.pocasi.ui.radar.label
import cz.kisiala.pocasi.ui.today.formatTemperature
import cz.kisiala.pocasi.ui.today.label
import cz.kisiala.pocasi.widget.WidgetColors
import cz.kisiala.pocasi.widget.WidgetDrawing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter

/** Upozornění s aktuálním počasím a radou k deštníku na nejbližší hodinu. */
object WeatherNotifier {

    private const val CHANNEL_ID = "scheduled_forecast"
    private const val NOTIFICATION_ID = 1
    private val HOUR = DateTimeFormatter.ofPattern("H:mm")

    /** Vytvoří kanál; u existujícího jen přeloží název a popis (po změně jazyka). */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_channel_description) },
        )
    }

    /** Stáhne předpověď a radar pro místo z nastavení a pošle upozornění (při chybě sítě krátké hlášení). */
    @SuppressLint("MissingPermission") // kontroluje areNotificationsEnabled()
    suspend fun notify(context: Context) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        ensureChannel(context)
        val container = (context.applicationContext as PocasiApp).container
        val settings = container.settingsRepository.notifications.first()
        // Poloha jen z aplikace (poslední zjištěná) – na pozadí ji bez „Povolit vždy“ zjistit nelze.
        val place = settings.place ?: container.placeRepository.lastLocation.first() ?: Place.FrydekMistek

        val (forecast, radar) = coroutineScope {
            val forecast = async {
                withTimeoutOrNull(FORECAST_TIMEOUT_MS) { withRetry { container.weatherRepository.forecastWithNowcast(place) } }
            }
            val radar = async {
                withTimeoutOrNull(RADAR_TIMEOUT_MS) { withRetry { container.radarRepository.latestFrame() } }
                    ?.let { NearbyPrecipitation.find(it.levels, place.latitude, place.longitude) to it.time }
            }
            forecast.await() to radar.await()
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        if (forecast == null) {
            builder
                .setContentTitle(place.name)
                .setContentText(context.getString(R.string.notif_error))
        } else {
            content(context, builder, place, forecast, radar?.first, radar?.second?.let(::formatRadarTime))
        }
        notifications.notify(NOTIFICATION_ID, builder.build())
    }

    private fun content(
        context: Context,
        builder: NotificationCompat.Builder,
        place: Place,
        forecast: Forecast,
        radar: NearbyPrecipitation?,
        radarTime: String?,
    ) {
        val c = forecast.current
        val title = context.getString(
            R.string.notif_title,
            formatTemperature(c.temperature),
            context.getString(c.condition.label),
            place.name,
        )
        val snow = c.temperature <= 1.0
        val advice = when (UmbrellaAdvice.from(forecast, radar)) {
            UmbrellaAdvice.RainingNow -> if (snow) R.string.notif_snowing_now else R.string.notif_raining_now
            UmbrellaAdvice.RainLikely -> if (snow) R.string.notif_snow_likely else R.string.notif_rain_likely
            UmbrellaAdvice.NotNeeded -> R.string.notif_umbrella_not_needed
        }.let(context::getString)

        val lines = buildList {
            add(advice)
            when {
                radar is NearbyPrecipitation.Found && radar.distanceKm > 0 && radarTime != null -> add(
                    context.getString(
                        R.string.notif_radar_found,
                        radarTime,
                        context.getString(radar.intensity.label),
                        radar.distanceKm,
                        directionAdverb(context, radar.bearing),
                    ),
                )
                radar == NearbyPrecipitation.None && radarTime != null -> add(
                    context.getString(R.string.notif_radar_none, radarTime, NearbyPrecipitation.SEARCH_RADIUS_KM.toInt()),
                )
            }
            UmbrellaAdvice.laterToday(forecast)?.let { add(context.getString(R.string.notif_rain_later, HOUR.format(it))) }
            add(
                context.getString(
                    R.string.notif_temperatures,
                    formatTemperature(forecast.todayMax),
                    formatTemperature(forecast.todayMin),
                    formatTemperature(c.apparentTemperature),
                ),
            )
        }

        // Ikona stejným kreslením jako v aplikaci; pozadí upozornění řídí systém, proto barvy podle systému.
        val colors = WidgetColors.resolve(context, ThemeMode.System)
        val icon = WidgetDrawing.icon(context, c.condition.icon, c.isDay, colors, 48.dp)
        builder
            .setContentTitle(title)
            .setContentText(advice)
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setLargeIcon(icon)
    }

    private fun directionAdverb(context: Context, bearing: Double): String {
        val names = context.resources.getStringArray(R.array.direction_adverb)
        return names[((bearing + 22.5) / 45).toInt() % 8]
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Ráno po probuzení telefonu síť chvíli naskakuje – zkusíme to několikrát; `null` = nepovedlo se. */
    private suspend fun <T> withRetry(block: suspend () -> T): T? {
        repeat(ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }
        }
        return null
    }

    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 5_000L
    // Obojí se vejde do času, který má přijímač budíku (viz NotificationAlarmReceiver).
    private const val FORECAST_TIMEOUT_MS = 35_000L
    private const val RADAR_TIMEOUT_MS = 25_000L
}

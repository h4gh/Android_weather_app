package cz.kisiala.pocasi.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import cz.kisiala.pocasi.PocasiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime

/**
 * Plánování upozornění: v AlarmManageru je vždy jen jeden budík – na nejbližší zapnutý čas.
 * Po jeho zazvonění se pošle upozornění a naplánuje další.
 */
object NotificationScheduler {

    /** Naplánuje (nebo zruší) budík podle aktuálního nastavení. */
    suspend fun reschedule(context: Context) {
        val settings = (context.applicationContext as PocasiApp).container.settingsRepository.notifications.first()
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context)
        // Rezerva, ať se právě odeslaný čas nenaplánuje znovu.
        val next = settings.nextAfter(ZonedDateTime.now().plusSeconds(30))
        if (next == null) {
            alarmManager.cancel(intent)
            return
        }
        val at = next.toInstant().toEpochMilli()
        // Bez oprávnění k přesným budíkům může systém upozornění o pár minut posunout.
        if (canScheduleExact(context)) {
            AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    fun canScheduleExact(context: Context): Boolean =
        AlarmManagerCompat.canScheduleExactAlarms(context.getSystemService(AlarmManager::class.java))

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, NotificationAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/** Budík naplánovaného upozornění: stáhne předpověď a radar, pošle upozornění, naplánuje další. */
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                NotificationScheduler.reschedule(appContext)
                // Přijímač na pozadí má na práci zhruba minutu – s rezervou.
                withTimeoutOrNull(RECEIVER_BUDGET_MS) { WeatherNotifier.notify(appContext) }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val RECEIVER_BUDGET_MS = 45_000L
    }
}

/** Po restartu telefonu, aktualizaci aplikace, změně času nebo oprávnění se budík naplánuje znovu. */
class NotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                NotificationScheduler.reschedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
